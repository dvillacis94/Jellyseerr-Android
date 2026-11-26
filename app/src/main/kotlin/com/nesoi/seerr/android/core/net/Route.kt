package com.nesoi.seerr.android.core.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One Seerr API call, under `/api/v1`. */
class Route(
    val method: String,
    val path: String,
    val body: JsonObject? = null,
    val timeoutSeconds: Long? = null,
)

object Routes {
    val status = Route("GET", "/status", timeoutSeconds = 10)
    val publicSettings = Route("GET", "/settings/public", timeoutSeconds = 10)
    val authMe = Route("GET", "/auth/me")
    val logout = Route("POST", "/auth/logout")

    fun loginLocal(email: String, password: String) =
        Route("POST", "/auth/local", buildJsonObject { put("email", email); put("password", password) })

    fun loginJellyfin(username: String, password: String) =
        Route("POST", "/auth/jellyfin", buildJsonObject { put("username", username); put("password", password) })

    fun loginPlex(authToken: String) =
        Route("POST", "/auth/plex", buildJsonObject { put("authToken", authToken) })
}
