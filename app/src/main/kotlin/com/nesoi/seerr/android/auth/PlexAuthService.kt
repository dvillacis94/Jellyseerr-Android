package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.core.net.ApiException
import com.nesoi.seerr.android.core.net.await
import com.nesoi.seerr.android.core.net.seerrJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Serializable
data class PlexPin(
    val id: Int,
    val code: String,
    val authToken: String? = null,
    val expiresAt: String? = null,
)

class PlexTimedOutException : Exception("The Plex sign-in timed out")

/** How Plex lists this app among a person's signed-in devices. */
data class PlexClientInfo(
    val clientIdentifier: String,
    val product: String,
    val version: String,
    val platform: String,
    val device: String,
)

/**
 * Plex's PIN-based sign-in, as Seerr's web client does it (`src/utils/plex.ts`): create a PIN,
 * send the person to app.plex.tv to approve it, then poll until the PIN carries an auth token.
 * That token is exchanged with Seerr through `POST /auth/plex`.
 *
 * [http] must not carry Seerr's cookies.
 */
class PlexAuthService(
    private val client: PlexClientInfo,
    private val http: OkHttpClient,
    private val plexUrl: HttpUrl = "https://plex.tv".toHttpUrl(),
) {
    suspend fun createPin(): PlexPin {
        val url = plexUrl.newBuilder().addPathSegments("api/v2/pins").addQueryParameter("strong", "true").build()
        return send(Request.Builder().url(url).post(ByteArray(0).toRequestBody()))
    }

    suspend fun checkPin(id: Int): PlexPin {
        val url = plexUrl.newBuilder().addPathSegments("api/v2/pins/$id").build()
        return send(Request.Builder().url(url).get())
    }

    /**
     * The page where the person approves the PIN.
     *
     * There is no forward URL: Plex sends non-HTTP(S) ones (such as an app scheme) to its home
     * page, and the PIN is never approved. Approval is seen by polling instead.
     */
    fun authUrl(pin: PlexPin): String {
        val params = listOf(
            "clientID" to client.clientIdentifier,
            "context[device][product]" to client.product,
            "context[device][version]" to client.version,
            "context[device][platform]" to client.platform,
            "context[device][device]" to client.device,
            "context[device][layout]" to "mobile",
            "code" to pin.code,
        )
        // Plex reads these from the fragment, so they can't go in the query.
        return "https://app.plex.tv/auth/#!?" + params.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }
    }

    /**
     * Polls until the PIN is approved, it expires ([PlexTimedOutException]) or the coroutine
     * is cancelled. Waits at most 15 minutes.
     */
    suspend fun pollForToken(pin: PlexPin, interval: Duration = 1.seconds): String {
        val untilExpiry = pin.expiresAt
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?.let { (it.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0).milliseconds }
        val patience = minOf(untilExpiry ?: 15.minutes, 15.minutes)
        var waited = Duration.ZERO
        while (waited < patience) {
            try {
                checkPin(pin.id).authToken?.takeIf { it.isNotEmpty() }?.let { return it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: IOException) {
                // A dropped connection shouldn't end the wait.
            } catch (_: ApiException) {
            }
            delay(interval)
            waited += interval
        }
        throw PlexTimedOutException()
    }

    private suspend fun send(request: Request.Builder): PlexPin {
        request
            .header("Accept", "application/json")
            .header("X-Plex-Product", client.product)
            .header("X-Plex-Version", client.version)
            .header("X-Plex-Client-Identifier", client.clientIdentifier)
            .header("X-Plex-Platform", client.platform)
            .header("X-Plex-Device", client.device)
        http.newCall(request.build()).await().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) throw ApiException.Http(response.code)
            return try {
                seerrJson.decodeFromString(PlexPin.serializer(), text)
            } catch (error: Exception) {
                throw ApiException.Decoding(error)
            }
        }
    }

    private fun encode(text: String): String = buildString {
        // Equivalent to JavaScript's encodeURIComponent.
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            val char = (byte.toInt() and 0xFF).toChar()
            if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-_.!~*'()") {
                append(char)
            } else {
                append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
    }
}
