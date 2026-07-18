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
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

class ProxyVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var localProxyServer: LocalProxyServer? = null
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    companion object {
        const val ACTION_CONNECT = "com.example.proxy.action.CONNECT"
        const val ACTION_DISCONNECT = "com.example.proxy.action.DISCONNECT"

        // Live connection state
        val isServiceRunning = MutableStateFlow(false)

        fun startVpn(context: Context) {
            val intent = Intent(context, ProxyVpnService::class.java).apply {
                action = ACTION_CONNECT
            }
            context.startService(intent)
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
                    startVpnInterface()
                }
                ACTION_DISCONNECT -> {
                    stopVpnInterface()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startVpnInterface() {
        // Stop any existing connection
        stopVpnInterface()

        val host = "change5.owlproxy.com"
        val port = 7778
        val pass = "5138110"

        // Generate username with random country code and random case
        val user = generateRandomizedUsername()

        Log.d("ProxyVpnService", "Starting VPN SOCKS5 connection using username: $user")

        try {
            // Start the local proxy forwarding server
            localProxyServer = LocalProxyServer(host, port, user, pass) { localPort ->
                Log.d("ProxyVpnService", "Local HTTP-to-SOCKS5 proxy server started on port: $localPort")

                // Build VPN Interface
                val builder = Builder()
                    .setSession("SolderVpnService")
                    .addAddress("10.8.0.2", 32)
                    .addRoute("10.8.0.0", 24) // Dummy local route to avoid black-holing DNS and physical IP traffic

                // Restrict to the 5 requested package names
                val allowedPackages = listOf(
                    "toolarafa.com",
                    "agcsuio.com",
                    "com.facebook.services",
                    "com.google.android.gsf",
                    "com.google.android.gms"
                )
                for (pkg in allowedPackages) {
                    try {
                        builder.addAllowedApplication(pkg)
                        Log.d("ProxyVpnService", "Added allowed application: $pkg")
                    } catch (e: Exception) {
                        Log.w("ProxyVpnService", "Could not add allowed application: $pkg (Not installed)")
                    }
                }

                // Set system-wide HTTP proxy configuration on the VPN interface (API 29+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val proxyInfo = ProxyInfo.buildDirectProxy("127.0.0.1", localPort)
                    builder.setHttpProxy(proxyInfo)
                    Log.d("ProxyVpnService", "Device-wide proxy set to local loopback 127.0.0.1:$localPort")
                }

                vpnInterface = builder.establish()
                isServiceRunning.value = true
            }
            localProxyServer?.start()

        } catch (e: Exception) {
            Log.e("ProxyVpnService", "Error establishing VPN connection", e)
            stopVpnInterface()
        }
    }

    private fun generateRandomizedUsername(): String {
        val countries = listOf("BD", "SL", "GN", "US", "DE", "ES", "FR")
        val country = countries.random()
        val randomizedCountry = country.map { char ->
            if (java.util.Random().nextBoolean()) char.uppercaseChar() else char.lowercaseChar()
        }.joinToString("")
        return "iZm3XTj3t830_custom_zone_$randomizedCountry"
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
    }
}

/**
 * Lightweight local HTTP proxy forwarding server that bridges to upstream SOCKS5.
 * Handles HTTPS CONNECT requests and plain HTTP requests by establishing a SOCKS5 tunnel to the destination.
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

            // Parse target host and port
            var targetHost: String
            var targetPort: Int

            if (method.equals("CONNECT", ignoreCase = true)) {
                // HTTPS CONNECT: target is usually "host:port"
                val hostPort = target.split(":")
                targetHost = hostPort[0]
                targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 443 else 443
            } else {
                // Plain HTTP: target is usually "http://host/path"
                var url = target
                if (url.startsWith("http://", ignoreCase = true)) {
                    url = url.substring(7)
                } else if (url.startsWith("https://", ignoreCase = true)) {
                    url = url.substring(8)
                }
                val pathIdx = url.indexOf('/')
                val hostPart = if (pathIdx != -1) url.substring(0, pathIdx) else url
                val hostPort = hostPart.split(":")
                targetHost = hostPort[0]
                targetPort = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 80 else 80
            }

            // Connect to actual upstream SOCKS5 proxy
            upstreamSocket = Socket(upstreamHost, upstreamPort)
            
            // Perform SOCKS5 authentication and handshake to target
            val handshakeSuccess = establishSocks5Tunnel(upstreamSocket, targetHost, targetPort, username, password)
            if (!handshakeSuccess) {
                clientSocket.close()
                upstreamSocket.close()
                return
            }

            val upstreamIn = upstreamSocket.getInputStream()
            val upstreamOut = upstreamSocket.getOutputStream()

            if (method.equals("CONNECT", ignoreCase = true)) {
                // Respond to client that connection is established
                clientOut.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.UTF_8))
                clientOut.flush()

                // Robust asynchronous bidirectional streaming:
                val upstreamFinal = upstreamSocket
                val clientFinal = clientSocket
                
                Thread {
                    try {
                        copyStream(clientIn, upstreamFinal.getOutputStream())
                    } catch (e: Exception) {
                    } finally {
                        try { clientFinal.close() } catch (e: Exception) {}
                        try { upstreamFinal.close() } catch (e: Exception) {}
                    }
                }.start()

                try {
                    copyStream(upstreamFinal.getInputStream(), clientOut)
                } catch (e: Exception) {
                } finally {
                    try { clientFinal.close() } catch (e: Exception) {}
                    try { upstreamFinal.close() } catch (e: Exception) {}
                }
            } else {
                // For plain HTTP, we must write the original request headers we read, followed by body
                val newHeaders = StringBuilder()
                newHeaders.append(requestLine).append("\r\n")
                for (i in 1 until lines.size) {
                    val line = lines[i]
                    if (line.isNotEmpty()) {
                        newHeaders.append(line).append("\r\n")
                    }
                }
                newHeaders.append("\r\n")

                upstreamOut.write(newHeaders.toString().toByteArray(Charsets.UTF_8))
                upstreamOut.flush()

                // Robust asynchronous bidirectional streaming:
                val upstreamFinal = upstreamSocket
                val clientFinal = clientSocket

                Thread {
                    try {
                        copyStream(clientIn, upstreamFinal.getOutputStream())
                    } catch (e: Exception) {
                    } finally {
                        try { clientFinal.close() } catch (e: Exception) {}
                        try { upstreamFinal.close() } catch (e: Exception) {}
                    }
                }.start()

                try {
                    copyStream(upstreamFinal.getInputStream(), clientOut)
                } catch (e: Exception) {
                } finally {
                    try { clientFinal.close() } catch (e: Exception) {}
                    try { upstreamFinal.close() } catch (e: Exception) {}
                }
            }

        } catch (e: Exception) {
            // Socket or IO error
        } finally {
            try { clientSocket.close() } catch (e: Exception) {}
            try { upstreamSocket?.close() } catch (e: Exception) {}
        }
    }

    private fun readExactly(inputStream: InputStream, buffer: ByteArray): Boolean {
        var bytesRead = 0
        while (bytesRead < buffer.size) {
            val result = inputStream.read(buffer, bytesRead, buffer.size - bytesRead)
            if (result == -1) return false
            bytesRead += result
        }
        return true
    }

    private fun establishSocks5Tunnel(
        upstreamSocket: Socket,
        targetHost: String,
        targetPort: Int,
        user: String,
        pass: String
    ): Boolean {
        try {
            val out = upstreamSocket.getOutputStream()
            val inp = upstreamSocket.getInputStream()

            // 1. Send SOCKS5 greeting (methods: No Auth [0x00], Username/Password [0x02])
            out.write(byteArrayOf(0x05, 0x02, 0x00, 0x02))
            out.flush()

            // 2. Read greeting response
            val response = ByteArray(2)
            if (!readExactly(inp, response) || response[0] != 0x05.toByte()) {
                Log.e("LocalProxyServer", "Invalid SOCKS5 greeting response")
                return false
            }

            val selectedMethod = response[1]
            if (selectedMethod == 0x02.toByte()) {
                // Username/Password authentication
                if (user.isEmpty()) {
                    Log.e("LocalProxyServer", "Upstream SOCKS5 requires auth but credentials are empty")
                    return false
                }
                val userBytes = user.toByteArray(Charsets.UTF_8)
                val passBytes = pass.toByteArray(Charsets.UTF_8)
                
                val authReq = ByteArray(3 + userBytes.size + passBytes.size)
                authReq[0] = 0x01 // Subnegotiation version
                authReq[1] = userBytes.size.toByte()
                System.arraycopy(userBytes, 0, authReq, 2, userBytes.size)
                authReq[2 + userBytes.size] = passBytes.size.toByte()
                System.arraycopy(passBytes, 0, authReq, 3 + userBytes.size, passBytes.size)
                
                out.write(authReq)
                out.flush()

                val authRes = ByteArray(2)
                if (!readExactly(inp, authRes) || authRes[0] != 0x01.toByte() || authRes[1] != 0x00.toByte()) {
                    Log.e("LocalProxyServer", "SOCKS5 auth failed")
                    return false
                }
            } else if (selectedMethod != 0x00.toByte()) {
                Log.e("LocalProxyServer", "Unsupported SOCKS5 auth method: $selectedMethod")
                return false
            }

            // 3. Send SOCKS5 CONNECT request
            val hostBytes = targetHost.toByteArray(Charsets.UTF_8)
            // SOCKS5 CONNECT request size: 3 bytes header + 1 byte addrType + 1 byte domainLength + domainBytes + 2 bytes port = 7 + domainBytes
            val req = ByteArray(7 + hostBytes.size)
            req[0] = 0x05
            req[1] = 0x01
            req[2] = 0x00
            req[3] = 0x03 // Domain name address type
            req[4] = hostBytes.size.toByte()
            System.arraycopy(hostBytes, 0, req, 5, hostBytes.size)
            
            // Port: 2 bytes (Big Endian)
            req[5 + hostBytes.size] = (targetPort shr 8 and 0xFF).toByte()
            req[6 + hostBytes.size] = (targetPort and 0xFF).toByte()

            out.write(req)
            out.flush()

            // 4. Read SOCKS5 CONNECT response
            val connResHeader = ByteArray(4)
            if (!readExactly(inp, connResHeader) || connResHeader[0] != 0x05.toByte()) {
                Log.e("LocalProxyServer", "Invalid SOCKS5 connection response header")
                return false
            }

            val status = connResHeader[1]
            if (status != 0x00.toByte()) {
                Log.e("LocalProxyServer", "SOCKS5 connect failed with status: $status")
                return false
            }

            val addrType = connResHeader[3]
            // Skip the bound address and port fields
            when (addrType) {
                0x01.toByte() -> { // IPv4 (4 bytes address + 2 bytes port)
                    val dummy = ByteArray(6)
                    if (!readExactly(inp, dummy)) return false
                }
                0x03.toByte() -> { // Domain name (1 byte length + length bytes + 2 bytes port)
                    val len = inp.read()
                    if (len == -1) return false
                    val dummy = ByteArray(len + 2)
                    if (!readExactly(inp, dummy)) return false
                }
                0x04.toByte() -> { // IPv6 (16 bytes address + 2 bytes port)
                    val dummy = ByteArray(18)
                    if (!readExactly(inp, dummy)) return false
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("LocalProxyServer", "SOCKS5 Handshake failed with exception", e)
            return false
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
