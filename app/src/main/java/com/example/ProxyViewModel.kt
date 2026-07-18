package com.example

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            ProxyVpnService.isServiceRunning.collect { running ->
                _isProxyActive.value = running
            }
        }
    }

    fun toggleProxy(context: Context, enabled: Boolean) {
        if (enabled) {
            ProxyVpnService.startVpn(context)
        } else {
            ProxyVpnService.stopVpn(context)
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
