package com.nesoi.seerr.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nesoi.seerr.android.AppContainer
import com.nesoi.seerr.android.auth.AuthService
import com.nesoi.seerr.android.auth.LoginModel
import com.nesoi.seerr.android.auth.PlexAuthService
import com.nesoi.seerr.android.auth.ServerSetupModel
import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.ServerInfo
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.media.SignedInModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** What the app is showing: the one place that decides between launch, setup, sign-in and the app. */
sealed interface AppState {
    data object Loading : AppState
    data class ServerSetup(val model: ServerSetupModel) : AppState
    data class Unreachable(val host: String?) : AppState
    data class Login(val model: LoginModel) : AppState
    data class SignedIn(val user: User, val serverUrl: HttpUrl, val model: SignedInModel) : AppState
}

class AppViewModel(
    private val auth: AuthService,
    private val plexFactory: (String?) -> PlexAuthService,
    private val api: ApiClient,
) : ViewModel() {
    constructor(container: AppContainer) : this(container.authService, container::plexAuthService, container.api)

    private val _state = MutableStateFlow<AppState>(AppState.Loading)
    val state: StateFlow<AppState> = _state.asStateFlow()

    init {
        // A 401 from any call means the session ran out: go back to sign-in, once.
        auth.onSessionExpired = ::sessionExpired
        start()
    }

    override fun onCleared() {
        auth.onSessionExpired = null
    }

    private fun sessionExpired() {
        val server = (_state.value as? AppState.SignedIn)?.serverUrl ?: return
        // Other calls in flight may report the same 401 straight after.
        _state.value = AppState.Loading
        viewModelScope.launch { reopenLogin(server) }
    }

    private suspend fun reopenLogin(server: HttpUrl) {
        try {
            showLogin(auth.publicSettings(), server)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _state.value = AppState.Unreachable(server.host)
        }
    }

    /** Restores the saved session, or shows setup or sign-in when there isn't one. */
    fun start() {
        _state.value = AppState.Loading
        viewModelScope.launch {
            try {
                val server = auth.savedServerUrl()
                if (server == null) {
                    showServerSetup()
                    return@launch
                }
                val user = auth.restoreSession()
                if (user != null) {
                    _state.value = signedIn(user, server)
                } else {
                    showLogin(auth.publicSettings(), server)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = AppState.Unreachable(auth.serverUrl?.host)
            }
        }
    }

    fun signOut() {
        val server = (state.value as? AppState.SignedIn)?.serverUrl ?: return
        viewModelScope.launch {
            auth.signOut()
            reopenLogin(server)
        }
    }

    fun changeServer() {
        viewModelScope.launch {
            auth.forgetServer()
            showServerSetup()
        }
    }

    private fun showServerSetup() {
        _state.value = AppState.ServerSetup(
            ServerSetupModel(viewModelScope, auth) { info: ServerInfo -> showLogin(info.settings, info.url) },
        )
    }

    private fun showLogin(settings: PublicSettings, server: HttpUrl) {
        val model = LoginModel(
            scope = viewModelScope,
            auth = auth,
            plex = plexFactory(settings.plexClientIdentifier),
            settings = settings,
            serverUrl = server,
            onSignedIn = { user -> _state.value = signedIn(user, server) },
        )
        _state.value = AppState.Login(model)
    }

    private fun signedIn(user: User, server: HttpUrl) = AppState.SignedIn(user, server, SignedInModel(viewModelScope, api))
}
