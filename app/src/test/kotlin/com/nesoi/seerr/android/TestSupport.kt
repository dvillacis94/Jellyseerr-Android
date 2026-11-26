package com.nesoi.seerr.android

import com.nesoi.seerr.android.auth.AuthService
import com.nesoi.seerr.android.auth.ServerPreferences
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.SecureStore
import com.nesoi.seerr.android.core.net.SessionCookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import mockwebserver3.MockResponse

class FakeSecureStore : SecureStore {
    val values = mutableMapOf<String, String>()
    override fun read(key: String) = values[key]
    override fun write(key: String, value: String) { values[key] = value }
    override fun delete(key: String) { values.remove(key) }
}

class FakeServerPreferences(var url: HttpUrl? = null) : ServerPreferences {
    override suspend fun serverUrl() = url
    override suspend fun setServerUrl(url: HttpUrl?) { this.url = url }
}

/** An [AuthService] wired to real OkHttp, for pointing at a MockWebServer. */
class AuthFixture(url: HttpUrl? = null) {
    val store = FakeSecureStore()
    val preferences = FakeServerPreferences(url)
    val cookies = SessionCookieJar(store)
    val http = OkHttpClient.Builder().cookieJar(cookies).build()
    val api = ApiClient(http, cookies)
    val auth = AuthService(api, cookies, preferences)
}

fun json(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

const val INITIALIZED_SETTINGS = """{"initialized":true,"applicationTitle":"Seerr","mediaServerType":1,"localLogin":true,"plexClientIdentifier":"abc"}"""
const val USER_JSON = """{"id":3,"email":"me@example.com","displayName":"Me","permissions":2}"""
