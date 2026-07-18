package com.example

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ProxyInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var localProxyServer: LocalProxyServer? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_CONNECT = "com.example.proxy.action.CONNECT"
        const val ACTION_DISCONNECT = "com.example.proxy.action.DISCONNECT"

        const val EXTRA_HOST = "com.example.proxy.extra.HOST"
        const val EXTRA_PORT = "com.example.proxy.extra.PORT"
        const val EXTRA_USER = "com.example.proxy.extra.USER"
        const val EXTRA_PASS = "com.example.proxy.extra.PASS"
        const val EXTRA_TYPE = "com.example.proxy.extra.TYPE"

        // Live connection state
        val isServiceRunning = MutableStateFlow(false)
        val connectedProxyName = MutableStateFlow<String?>(null)

        fun startVpn(context: Context, proxy: ProxyEntity) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_HOST, proxy.host)
                putExtra(EXTRA_PORT, proxy.port)
                putExtra(EXTRA_USER, proxy.username)
                putExtra(EXTRA_PASS, proxy.password)
                putExtra(EXTRA_TYPE, proxy.type)
                putExtra("PROXY_NAME", proxy.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        stopVpnInterface()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            when (intent.action) {
                ACTION_CONNECT -> {
                    val host = intent.getStringExtra(EXTRA_HOST) ?: ""
                    val port = intent.getIntExtra(EXTRA_PORT, 8080)
                    val user = intent.getStringExtra(EXTRA_USER) ?: ""
                    val pass = intent.getStringExtra(EXTRA_PASS) ?: ""
                    val type = intent.getStringExtra(EXTRA_TYPE) ?: "HTTP"
                    val name = intent.getStringExtra("PROXY_NAME") ?: "Proxy"

                    startVpnInterface(host, port, user, pass, type, name)
                }
                ACTION_DISCONNECT -> {
                    stopVpnInterface()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpnInterface(host: String, port: Int, user: String, pass: String, type: String, name: String) {
        // Stop any existing connection
        stopVpnInterface()

        Log.d("ProxyVpnService", "Starting VPN connection to proxy: $host:$port ($type)")

        try {
            // Start the local proxy forwarding server
            localProxyServer = LocalProxyServer(host, port, user, pass) { localPort ->
                Log.d("ProxyVpnService", "Local proxy server started on port: $localPort")

                // Build VPN Interface
                val builder = Builder()
                    .setSession("ProxyVpnService")
                    .addAddress("10.8.0.2", 32)
                    .addRoute("10.8.0.0", 24) // Dummy network route to prevent packet black-holing

                // Set system-wide HTTP proxy configuration on the VPN interface (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val proxyInfo = ProxyInfo.buildDirectProxy("127.0.0.1", localPort)
                    builder.setHttpProxy(proxyInfo)
                    Log.d("ProxyVpnService", "Device-wide proxy set to local loopback 127.0.0.1:$localPort")
                }

                vpnInterface = builder.establish()

                isServiceRunning.value = true
                connectedProxyName.value = name
            }
            localProxyServer?.start()

        } catch (e: Exception) {
            Log.e("ProxyVpnService", "Error establishing VPN connection", e)
            stopVpnInterface()
        }
    }

    private fun stopVpnInterface() {
        Log.d("ProxyVpnService", "Stopping VPN connection")
        try {
            localProxyServer?.stop()
            localProxyServer = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            e.printStackTrace()
        }

        isServiceRunning.value = false
        connectedProxyName.value = null
    }
}

/**
 * Lightweight HTTP proxy forwarding server.
 * Handles HTTPS CONNECT requests and plain HTTP requests by forwarding them to the upstream proxy.
 * Adds upstream Basic Authentication if credentials are provided.
 */
class LocalProxyServer(
    private val upstreamHost: String,
    private val upstreamPort: Int,
    private val username: String,
    private val password: String,
    private val onPortSelected: (Int) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(0) // Bind to any free port
                val port = serverSocket!!.localPort
                onPortSelected(port)

                while (isRunning) {
                    val clientSocket = serverSocket!!.accept()
                    Thread {
                        handleClient(clientSocket)
                    }.start()
                }
            } catch (e: Exception) {
                Log.e("LocalProxyServer", "Server exception", e)
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(clientSocket: Socket) {
        var upstreamSocket: Socket? = null
        try {
            val clientIn = clientSocket.getInputStream()
            val clientOut = clientSocket.getOutputStream()

            val headerBytes = readHeaders(clientIn)
            if (headerBytes.isEmpty()) {
                clientSocket.close()
                return
            }

            val headerStr = String(headerBytes, Charsets.UTF_8)
            val lines = headerStr.split("\r\n")
            if (lines.isEmpty()) {
                clientSocket.close()
                return
            }

            val requestLine = lines[0]
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                clientSocket.close()
                return
            }

            val method = parts[0]
            val target = parts[1]

            // Connect to actual upstream proxy
            upstreamSocket = Socket(upstreamHost, upstreamPort)
            val upstreamIn = upstreamSocket.getInputStream()
            val upstreamOut = upstreamSocket.getOutputStream()

            val authHeader = if (username.isNotEmpty()) {
                val credentials = "$username:$password"
                val encoded = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                "Proxy-Authorization: Basic $encoded\r\n"
            } else {
                ""
            }

            if (method.equals("CONNECT", ignoreCase = true)) {
                // HTTPS Tunneling
                val connectRequest = "CONNECT $target HTTP/1.1\r\n" +
                        "Host: $target\r\n" +
                        authHeader +
                        "\r\n"
                upstreamOut.write(connectRequest.toByteArray(Charsets.UTF_8))
                upstreamOut.flush()

                val upstreamHeaders = readHeaders(upstreamIn)
                val responseStr = String(upstreamHeaders, Charsets.UTF_8)
                if (responseStr.contains("200")) {
                    clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.UTF_8))
                    clientOut.flush()

                    val t1 = Thread { copyStream(clientIn, upstreamOut) }
                    val t2 = Thread { copyStream(upstreamIn, clientOut) }
                    t1.start()
                    t2.start()
                    t1.join()
                    t2.join()
                } else {
                    clientOut.write(upstreamHeaders)
                    clientOut.flush()
                }
            } else {
                // Plain HTTP Request
                val newHeaders = StringBuilder()
                newHeaders.append(requestLine).append("\r\n")
                if (username.isNotEmpty()) {
                    newHeaders.append(authHeader)
                }
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    if (line.isNotEmpty()) {
                        if (!line.startsWith("Proxy-Authorization:", ignoreCase = true)) {
                            newHeaders.append(line).append("\r\n")
                        }
                    }
                }
                newHeaders.append("\r\n")

                upstreamOut.write(newHeaders.toString().toByteArray(Charsets.UTF_8))
                upstreamOut.flush()

                val t1 = Thread { copyStream(clientIn, upstreamOut) }
                val t2 = Thread { copyStream(upstreamIn, clientOut) }
                t1.start()
                t2.start()
                t1.join()
                t2.join()
            }

        } catch (e: Exception) {
            // Socket or IO error
        } finally {
            try { clientSocket.close() } catch (e: Exception) {}
            try { upstreamSocket?.close() } catch (e: Exception) {}
        }
    }

    private fun readHeaders(inputStream: InputStream): ByteArray {
        val bos = ByteArrayOutputStream()
        var state = 0
        try {
            while (isRunning) {
                val b = inputStream.read()
                if (b == -1) break
                bos.write(b)
                if (state == 0 && b == '\r'.toInt()) {
                    state = 1
                } else if (state == 1 && b == '\n'.toInt()) {
                    state = 2
                } else if (state == 2 && b == '\r'.toInt()) {
                    state = 3
                } else if (state == 3 && b == '\n'.toInt()) {
                    break
                } else {
                    state = 0
                }
            }
        } catch (e: Exception) {
            // Input stream closed
        }
        return bos.toByteArray()
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16384)
        try {
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
                output.flush()
            }
        } catch (e: Exception) {
            // Stream closed
        }
    }
}
