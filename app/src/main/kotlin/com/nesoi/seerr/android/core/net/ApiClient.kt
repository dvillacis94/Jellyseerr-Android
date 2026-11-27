package com.nesoi.seerr.android.core.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.serializer
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Talks to one Seerr server. A cancelled coroutine cancels its request and is never an error. */
class ApiClient(
    private val http: OkHttpClient,
    private val cookies: SessionCookieJar,
    baseUrl: HttpUrl? = null,
) {
    /** The server API calls go to. */
    @Volatile
    var baseUrl: HttpUrl? = baseUrl

    /** Called when the server turns down a call that needs a session, so the app can ask for sign-in again. */
    @Volatile
    var onUnauthorized: (() -> Unit)? = null

    /**
     * @param baseUrl Overrides the configured server, such as to probe a candidate during setup.
     * @throws IOException when the server can't be reached.
     */
    suspend fun <T> request(route: Route, serializer: KSerializer<T>, baseUrl: HttpUrl? = null): T {
        val text = send(route, baseUrl)
        return try {
            seerrJson.decodeFromString(serializer, text)
        } catch (error: Exception) {
            throw ApiException.Decoding(error)
        }
    }

    /** For calls whose response body isn't needed. */
    suspend fun requestUnit(route: Route, baseUrl: HttpUrl? = null) {
        send(route, baseUrl)
    }

    private suspend fun send(route: Route, override: HttpUrl?): String {
        val base = override ?: baseUrl ?: throw IOException("No server is configured")
        val url = base.newBuilder().addPathSegments("api/v1" + route.path).apply {
            route.query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()

        val builder = Request.Builder().url(url).header("Accept", "application/json")
        if (route.method == "GET") {
            builder.get()
        } else {
            val body = route.body?.toString().orEmpty().ifEmpty { "{}" }
            builder.method(route.method, body.toRequestBody(JSON_MEDIA_TYPE))
            // Servers with CSRF protection want the XSRF-TOKEN cookie echoed back on writes.
            cookies.loadForRequest(url).firstOrNull { it.name == "XSRF-TOKEN" }?.let {
                builder.header("X-XSRF-TOKEN", it.value)
            }
        }

        val client = route.timeoutSeconds
            ?.let { http.newBuilder().callTimeout(it, TimeUnit.SECONDS).build() }
            ?: http
        client.newCall(builder.build()).await().use { response ->
            val text = response.body.string()
            if (response.code == 401 && route.requiresSession) onUnauthorized?.invoke()
            validate(response.code, text)
            return text
        }
    }

    private fun validate(code: Int, body: String) {
        val message = serverMessage(body)
        when (code) {
            in 200..299 -> return
            401 -> throw ApiException.Unauthorized(message)
            403 -> throw ApiException.Forbidden(message)
            404 -> throw ApiException.NotFound
            409 -> throw ApiException.Conflict(message)
            400, 422 -> throw ApiException.Server(message ?: "Validation error")
            in 500..599 -> throw ApiException.Server(message ?: "Server error $code")
            else -> throw ApiException.Http(code)
        }
    }

    /** Seerr reports errors as `{ "message": … }`, or `{ "error": … }` from some auth routes. */
    private fun serverMessage(body: String): String? {
        val root = runCatching { seerrJson.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
        fun text(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull
        return text("message") ?: text("error")
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

suspend inline fun <reified T> ApiClient.fetch(route: Route, baseUrl: HttpUrl? = null): T =
    request(route, serializer<T>(), baseUrl)

val seerrJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

internal suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, _, _ -> response.close() }
        }
    })
}
