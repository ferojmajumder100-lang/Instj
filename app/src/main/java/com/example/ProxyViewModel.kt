package com.example

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

sealed class ProxyTestResult {
    object Idle : ProxyTestResult()
    object Loading : ProxyTestResult()
    data class Success(
        val ip: String,
        val country: String,
        val city: String,
        val region: String,
        val isp: String,
        val latencyMs: Long
    ) : ProxyTestResult()
    data class Error(val message: String) : ProxyTestResult()
}

class ProxyViewModel(private val repository: ProxyRepository) : ViewModel() {

    val allProxies: StateFlow<List<ProxyEntity>> = repository.allProxies
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeProxy = MutableStateFlow<ProxyEntity?>(null)
    val activeProxy: StateFlow<ProxyEntity?> = _activeProxy.asStateFlow()

    private val _isProxyActive = MutableStateFlow(false)
    val isProxyActive: StateFlow<Boolean> = _isProxyActive.asStateFlow()

    private val _testStatus = MutableStateFlow<ProxyTestResult>(ProxyTestResult.Idle)
    val testStatus: StateFlow<ProxyTestResult> = _testStatus.asStateFlow()

    init {
        viewModelScope.launch {
            ProxyVpnService.isServiceRunning.collect { running ->
                _isProxyActive.value = running
            }
        }
    }

    fun selectProxy(proxy: ProxyEntity?, context: Context) {
        viewModelScope.launch {
            _activeProxy.value = proxy
            // If proxy was active, update the active system override
            if (_isProxyActive.value && proxy != null) {
                ProxyVpnService.startVpn(context, proxy)
            }
        }
    }

    fun toggleProxy(context: Context, enabled: Boolean) {
        val proxy = _activeProxy.value
        if (enabled && proxy != null) {
            ProxyVpnService.startVpn(context, proxy)
        } else {
            ProxyVpnService.stopVpn(context)
        }
    }

    fun testProxyConnection() {
        val proxy = _activeProxy.value
        val useProxy = _isProxyActive.value && proxy != null

        viewModelScope.launch {
            _testStatus.value = ProxyTestResult.Loading
            val startTime = System.currentTimeMillis()

            try {
                val responseBody = withContext(Dispatchers.IO) {
                    val clientBuilder = OkHttpClient.Builder()
                        .connectTimeout(8, TimeUnit.SECONDS)
                        .readTimeout(8, TimeUnit.SECONDS)

                    if (useProxy && proxy != null) {
                        val netProxy = Proxy(
                            Proxy.Type.HTTP,
                            InetSocketAddress.createUnresolved(proxy.host, proxy.port)
                        )
                        clientBuilder.proxy(netProxy)

                        if (proxy.username.isNotEmpty()) {
                            clientBuilder.proxyAuthenticator { _, response ->
                                val credential = okhttp3.Credentials.basic(proxy.username, proxy.password)
                                response.request.newBuilder()
                                    .header("Proxy-Authorization", credential)
                                    .build()
                            }
                        }
                    }

                    val client = clientBuilder.build()
                    // Use a reliable IP lookup service
                    val request = Request.Builder()
                        .url("http://ip-api.com/json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw Exception("Response code: ${response.code}")
                        response.body?.string()
                    }
                }

                val latency = System.currentTimeMillis() - startTime

                if (responseBody != null) {
                    val json = JSONObject(responseBody)
                    if (json.optString("status") == "fail") {
                        val message = json.optString("message", "API query failed")
                        _testStatus.value = ProxyTestResult.Error(message)
                    } else {
                        val ip = json.optString("query", "Unknown IP")
                        val country = json.optString("country", "Unknown Country")
                        val city = json.optString("city", "Unknown City")
                        val region = json.optString("regionName", "Unknown Region")
                        val isp = json.optString("isp", "Unknown ISP")

                        _testStatus.value = ProxyTestResult.Success(
                            ip = ip,
                            country = country,
                            city = city,
                            region = region,
                            isp = isp,
                            latencyMs = latency
                        )
                    }
                } else {
                    _testStatus.value = ProxyTestResult.Error("Empty response body from lookup server")
                }

            } catch (e: Exception) {
                // If ip-api fails, try ipapi.co as a fallback
                try {
                    val responseBodyFallback = withContext(Dispatchers.IO) {
                        val clientBuilder = OkHttpClient.Builder()
                            .connectTimeout(8, TimeUnit.SECONDS)
                            .readTimeout(8, TimeUnit.SECONDS)

                        if (useProxy && proxy != null) {
                            val netProxy = Proxy(
                                Proxy.Type.HTTP,
                                InetSocketAddress.createUnresolved(proxy.host, proxy.port)
                            )
                            clientBuilder.proxy(netProxy)

                            if (proxy.username.isNotEmpty()) {
                                clientBuilder.proxyAuthenticator { _, response ->
                                    val credential = okhttp3.Credentials.basic(proxy.username, proxy.password)
                                    response.request.newBuilder()
                                        .header("Proxy-Authorization", credential)
                                        .build()
                                }
                            }
                        }

                        val client = clientBuilder.build()
                        val request = Request.Builder()
                            .url("https://ipapi.co/json/")
                            .build()

                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw Exception("Fallback response code: ${response.code}")
                            response.body?.string()
                        }
                    }

                    val latency = System.currentTimeMillis() - startTime
                    if (responseBodyFallback != null) {
                        val json = JSONObject(responseBodyFallback)
                        val ip = json.optString("ip", "Unknown IP")
                        val country = json.optString("country_name", "Unknown Country")
                        val city = json.optString("city", "Unknown City")
                        val region = json.optString("region", "Unknown Region")
                        val isp = json.optString("org", "Unknown ISP")

                        _testStatus.value = ProxyTestResult.Success(
                            ip = ip,
                            country = country,
                            city = city,
                            region = region,
                            isp = isp,
                            latencyMs = latency
                        )
                    } else {
                        _testStatus.value = ProxyTestResult.Error(e.localizedMessage ?: "Connection error")
                    }
                } catch (fallbackEx: Exception) {
                    _testStatus.value = ProxyTestResult.Error(fallbackEx.localizedMessage ?: "Connection failed")
                }
            }
        }
    }

    // Proxy database CRUD
    fun addProxy(proxy: ProxyEntity) {
        viewModelScope.launch {
            repository.insert(proxy)
        }
    }

    fun updateProxy(proxy: ProxyEntity) {
        viewModelScope.launch {
            repository.update(proxy)
        }
    }

    fun deleteProxy(proxy: ProxyEntity, context: Context) {
        viewModelScope.launch {
            repository.delete(proxy)
            if (_activeProxy.value?.id == proxy.id) {
                _activeProxy.value = null
                ProxyVpnService.stopVpn(context)
            }
        }
    }
}

class ProxyViewModelFactory(private val repository: ProxyRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProxyViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProxyViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
