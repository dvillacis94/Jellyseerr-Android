package com.nesoi.seerr.android

import android.content.Context
import android.os.Build
import com.nesoi.seerr.android.auth.AuthService
import com.nesoi.seerr.android.auth.DataStoreServerPreferences
import com.nesoi.seerr.android.auth.PlexAuthService
import com.nesoi.seerr.android.auth.PlexClientInfo
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.KeystoreSecureStore
import com.nesoi.seerr.android.core.net.SessionCookieJar
import com.nesoi.seerr.android.media.DataStorePreferences
import okhttp3.CookieJar
import okhttp3.OkHttpClient
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Everything the app shares, built once at launch and handed to whatever needs it. */
class AppContainer(private val context: Context) {
    private val cookies = SessionCookieJar(KeystoreSecureStore(context))

    private val http = OkHttpClient.Builder()
        .cookieJar(cookies)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api = ApiClient(http, cookies)

    val filterStorage = DataStorePreferences(context)

    val authService = AuthService(api, cookies, DataStoreServerPreferences(context))

    /** Talks to plex.tv, so it never carries Seerr's cookies. */
    fun plexAuthService(clientIdentifier: String?): PlexAuthService {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        return PlexAuthService(
            client = PlexClientInfo(
                clientIdentifier = clientIdentifier ?: fallbackPlexClientIdentifier(),
                product = "Seerr Android",
                version = version,
                platform = "Android",
                device = Build.MODEL,
            ),
            http = http.newBuilder().cookieJar(CookieJar.NO_COOKIES).build(),
        )
    }

    /** Only used if a server leaves `plexClientIdentifier` out of its public settings. */
    private fun fallbackPlexClientIdentifier(): String {
        val prefs = context.getSharedPreferences("plex", Context.MODE_PRIVATE)
        return prefs.getString("client_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("client_id", it).apply() }
    }
}
