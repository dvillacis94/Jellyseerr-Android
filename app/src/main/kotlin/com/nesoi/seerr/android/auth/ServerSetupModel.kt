package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.core.ServerConnectionError
import com.nesoi.seerr.android.core.ServerConnectionException
import com.nesoi.seerr.android.core.ServerInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServerSetupState(
    val address: String = "",
    val isConnecting: Boolean = false,
    val error: ServerConnectionError? = null,
) {
    val canConnect: Boolean get() = !isConnecting && address.isNotBlank()
}

/** The server address screen. */
class ServerSetupModel(
    private val scope: CoroutineScope,
    private val auth: AuthService,
    private val onConnected: (ServerInfo) -> Unit,
) {
    private val _state = MutableStateFlow(ServerSetupState())
    val state: StateFlow<ServerSetupState> = _state.asStateFlow()

    fun setAddress(address: String) = _state.update { it.copy(address = address, error = null) }

    fun connect() {
        val current = _state.value
        if (!current.canConnect) return
        _state.update { it.copy(isConnecting = true, error = null) }
        scope.launch {
            try {
                val info = auth.discoverServer(current.address)
                auth.useServer(info.url)
                _state.update { it.copy(isConnecting = false) }
                onConnected(info)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ServerConnectionException) {
                _state.update { it.copy(isConnecting = false, error = error.error) }
            }
        }
    }
}
