package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.ServerAddress
import com.nesoi.seerr.android.core.ServerConnectionError
import com.nesoi.seerr.android.core.ServerConnectionException
import com.nesoi.seerr.android.core.ServerInfo
import com.nesoi.seerr.android.core.ServerStatus
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.ApiException
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.SessionCookieJar
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl
import java.io.IOException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

sealed interface SignInMethod {
    data class Local(val email: String, val password: String) : SignInMethod
    data class Jellyfin(val username: String, val password: String) : SignInMethod
    data class Plex(val authToken: String) : SignInMethod
}

/** Why a sign-in didn't work. The screen turns these into text. */
sealed interface SignInFailure {
    data object InvalidCredentials : SignInFailure
    data object AccessDenied : SignInFailure
    data object MethodDisabled : SignInFailure
    data object Unreachable : SignInFailure
    data object PlexTimedOut : SignInFailure
    data class Message(val text: String) : SignInFailure
}

class SignInException(val failure: SignInFailure) : Exception(failure.toString())

class AuthService(
    private val api: ApiClient,
    private val cookies: SessionCookieJar,
    private val preferences: ServerPreferences,
) {
    /** The server API calls go to, once one is configured. */
    val serverUrl: HttpUrl? get() = api.baseUrl

    /** Called when the server says the session ran out, after it was dropped on this device. */
    @Volatile
    var onSessionExpired: (() -> Unit)? = null

    init {
        api.onUnauthorized = {
            cookies.clear()
            onSessionExpired?.invoke()
        }
    }

    // Server

    /**
     * Finds the Seerr server behind what the user typed, trying HTTPS before HTTP when no
     * scheme was given. Doesn't change the configured server.
     *
     * A certificate problem on HTTPS ends the search: falling back to HTTP then would let
     * someone on the network read the sign-in by presenting a bad certificate.
     */
    suspend fun discoverServer(input: String): ServerInfo {
        var failure = ServerConnectionError.Unreachable
        for (url in ServerAddress.candidates(input)) {
            try {
                val status = api.fetch<ServerStatus>(Routes.status, url)
                val settings = api.fetch<PublicSettings>(Routes.publicSettings, url)
                if (!settings.initialized) throw ServerConnectionException(ServerConnectionError.NotInitialized)
                return ServerInfo(url, status, settings)
            } catch (error: CancellationException) {
                throw error
            } catch (error: ServerConnectionException) {
                throw error
            } catch (error: IOException) {
                if (error.isCertificateProblem()) {
                    throw ServerConnectionException(ServerConnectionError.UntrustedCertificate)
                }
            } catch (error: ApiException) {
                // The host answered, but not the way Seerr does (404, HTML and so on).
                failure = ServerConnectionError.NotSeerr
            }
        }
        throw ServerConnectionException(failure)
    }

    /** Makes [url] the server all API calls go to. */
    suspend fun useServer(url: HttpUrl) {
        if (preferences.serverUrl() != url) cookies.clear()
        preferences.setServerUrl(url)
        api.baseUrl = url
    }

    /** Signs out (best effort) and forgets the configured server. */
    suspend fun forgetServer() {
        signOut()
        preferences.setServerUrl(null)
        api.baseUrl = null
    }

    suspend fun publicSettings(): PublicSettings = api.fetch(Routes.publicSettings)

    // Session

    /** @throws SignInException when the server turns the sign-in down or can't be reached. */
    suspend fun signIn(method: SignInMethod): User {
        val route = when (method) {
            is SignInMethod.Local -> Routes.loginLocal(method.email, method.password)
            is SignInMethod.Jellyfin -> Routes.loginJellyfin(method.username, method.password)
            is SignInMethod.Plex -> Routes.loginPlex(method.authToken)
        }
        try {
            val user = api.fetch<User>(route)
            didAuthenticate()
            return user
        } catch (error: ApiException) {
            throw SignInException(signInFailure(error, method))
        } catch (error: IOException) {
            throw SignInException(SignInFailure.Unreachable)
        }
    }

    /**
     * The signed-in user, or null if there's no valid session. Reads the configured server
     * back from storage, so call it first at launch.
     *
     * @throws IOException when the server can't be reached.
     */
    suspend fun restoreSession(): User? {
        val url = preferences.serverUrl() ?: return null
        api.baseUrl = url
        cookies.restoreIfNeeded(url)
        return try {
            api.fetch<User>(Routes.authMe).also { didAuthenticate() }
        } catch (_: ApiException.Unauthorized) {
            cookies.clear()
            null
        } catch (_: ApiException.Forbidden) {
            cookies.clear()
            null
        }
    }

    suspend fun signOut() {
        try {
            api.requestUnit(Routes.logout)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Best effort: the session is dropped on this device either way.
        }
        cookies.clear()
    }

    /** The configured server, if one was saved. */
    suspend fun savedServerUrl(): HttpUrl? = preferences.serverUrl()

    private fun didAuthenticate() {
        api.baseUrl?.let(cookies::persist)
    }

    internal companion object {
        fun signInFailure(error: ApiException, method: SignInMethod): SignInFailure = when {
            error is ApiException.Unauthorized -> SignInFailure.InvalidCredentials
            // Local sign-in answers a wrong password with 403; Plex and Jellyfin use 403 for
            // accounts without access to the server.
            error is ApiException.Forbidden && method is SignInMethod.Local -> SignInFailure.InvalidCredentials
            error is ApiException.Forbidden && error.serverMessage == "INVALID_CREDENTIALS" ->
                SignInFailure.InvalidCredentials
            error is ApiException.Forbidden -> SignInFailure.AccessDenied
            error is ApiException.Server && error.serverMessage.contains("disabled", ignoreCase = true) ->
                SignInFailure.MethodDisabled
            error is ApiException.Server && error.serverMessage == "INVALID_CREDENTIALS" ->
                SignInFailure.InvalidCredentials
            error is ApiException.Server -> SignInFailure.Message(error.serverMessage)
            else -> SignInFailure.Message(error.message.orEmpty())
        }

        private fun Throwable.isCertificateProblem(): Boolean =
            generateSequence(this) { it.cause }.any { it is CertificateException || it is SSLPeerUnverifiedException }
    }
}
