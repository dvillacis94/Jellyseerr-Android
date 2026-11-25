package com.nesoi.seerr.android.core.net

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Holds the Seerr session cookie (`connect.sid`) and CSRF token, and backs the session cookie
 * up in a [SecureStore] so the sign-in survives the app being killed.
 */
class SessionCookieJar(
    private val store: SecureStore,
    private val now: () -> Long = System::currentTimeMillis,
) : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            if (cookie.expiresAt > now()) this.cookies += cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= now() }
        return cookies.filter { it.matches(url) }
    }

    /** Saves the current session cookie for [serverUrl], if there is one. */
    @Synchronized
    fun persist(serverUrl: HttpUrl) {
        val cookie = sessionCookie(serverUrl) ?: return
        store.write(KEY, "$serverUrl\n$cookie")
    }

    /** Puts the backed-up cookie back if it's missing, and belongs to [serverUrl]. */
    @Synchronized
    fun restoreIfNeeded(serverUrl: HttpUrl) {
        if (sessionCookie(serverUrl) != null) return
        val stored = store.read(KEY) ?: return
        val (storedUrl, header) = stored.split("\n", limit = 2).takeIf { it.size == 2 } ?: return
        if (storedUrl.toHttpUrlOrNull() != serverUrl) return
        val cookie = Cookie.parse(serverUrl, header) ?: return
        if (cookie.expiresAt > now()) cookies += cookie
    }

    /** Forgets every cookie, the session and the backup. */
    @Synchronized
    fun clear() {
        cookies.clear()
        store.delete(KEY)
    }

    private fun sessionCookie(serverUrl: HttpUrl) =
        cookies.firstOrNull { it.name == SESSION_COOKIE && it.expiresAt > now() && it.matches(serverUrl) }

    private companion object {
        const val SESSION_COOKIE = "connect.sid"
        const val KEY = "session"
    }
}
