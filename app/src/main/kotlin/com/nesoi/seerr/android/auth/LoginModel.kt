package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

enum class PasswordMode { MediaServer, Local }

data class LoginState(
    val passwordMode: PasswordMode,
    val username: String = "",
    val email: String = "",
    val password: String = "",
    val isSigningIn: Boolean = false,
    val isWaitingForPlex: Boolean = false,
    val failure: SignInFailure? = null,
) {
    fun canSubmitPassword(): Boolean {
        if (isSigningIn || password.isEmpty()) return false
        return when (passwordMode) {
            PasswordMode.MediaServer -> username.isNotBlank()
            PasswordMode.Local -> email.isNotBlank()
        }
    }
}

/** The sign-in screen for one server. */
class LoginModel(
    private val scope: CoroutineScope,
    private val auth: AuthService,
    private val plex: PlexAuthService?,
    val settings: PublicSettings,
    val serverUrl: HttpUrl,
    private val onSignedIn: (User) -> Unit,
) {
    val options = LoginOptions.from(settings)

    private val _state = MutableStateFlow(
        LoginState(passwordMode = if (options.mediaServer != null) PasswordMode.MediaServer else PasswordMode.Local),
    )
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val browserRequests = Channel<String>(Channel.BUFFERED)

    /** Pages to open in the browser, such as Plex's approval page. */
    val browserUrls: Flow<String> = browserRequests.receiveAsFlow()

    private var plexJob: Job? = null

    fun setPasswordMode(mode: PasswordMode) = _state.update { it.copy(passwordMode = mode, failure = null) }
    fun setUsername(value: String) = _state.update { it.copy(username = value) }
    fun setEmail(value: String) = _state.update { it.copy(email = value) }
    fun setPassword(value: String) = _state.update { it.copy(password = value) }

    fun signInWithPassword() {
        val current = _state.value
        if (!current.canSubmitPassword()) return
        val method = when (current.passwordMode) {
            PasswordMode.MediaServer -> SignInMethod.Jellyfin(current.username.trim(), current.password)
            PasswordMode.Local -> SignInMethod.Local(current.email.trim(), current.password)
        }
        _state.update { it.copy(isSigningIn = true, failure = null) }
        scope.launch { signIn(method) }
    }

    fun signInWithPlex() {
        val plex = plex ?: return
        if (_state.value.isSigningIn) return
        _state.update { it.copy(isSigningIn = true, isWaitingForPlex = true, failure = null) }
        plexJob = scope.launch {
            try {
                val pin = plex.createPin()
                browserRequests.send(plex.authUrl(pin))
                val token = plex.pollForToken(pin)
                _state.update { it.copy(isWaitingForPlex = false) }
                signIn(SignInMethod.Plex(token))
            } catch (error: CancellationException) {
                throw error
            } catch (_: PlexTimedOutException) {
                finishWithFailure(SignInFailure.PlexTimedOut)
            } catch (_: java.io.IOException) {
                finishWithFailure(SignInFailure.Unreachable)
            } catch (error: Exception) {
                finishWithFailure(SignInFailure.Message(error.message.orEmpty()))
            }
        }
    }

    /** The person gave up waiting for Plex. */
    fun cancelPlex() {
        plexJob?.cancel()
        plexJob = null
        _state.update { it.copy(isSigningIn = false, isWaitingForPlex = false) }
    }

    private suspend fun signIn(method: SignInMethod) {
        try {
            val user = auth.signIn(method)
            _state.update { it.copy(isSigningIn = false, password = "") }
            onSignedIn(user)
        } catch (error: SignInException) {
            finishWithFailure(error.failure)
        }
    }

    private fun finishWithFailure(failure: SignInFailure) =
        _state.update { it.copy(isSigningIn = false, isWaitingForPlex = false, failure = failure) }
}
