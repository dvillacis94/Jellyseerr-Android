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
    val query: List<Pair<String, String>> = emptyList(),
    /**
     * Whether a 401 means the session ran out. False for calls made while signing in or
     * before there is a session, where a 401 is just the answer.
     */
    val requiresSession: Boolean = true,
)

object Routes {
    val status = Route("GET", "/status", timeoutSeconds = 10, requiresSession = false)
    val publicSettings = Route("GET", "/settings/public", timeoutSeconds = 10, requiresSession = false)
    val authMe = Route("GET", "/auth/me", requiresSession = false)
    val logout = Route("POST", "/auth/logout", requiresSession = false)

    fun loginLocal(email: String, password: String) =
        Route("POST", "/auth/local", buildJsonObject { put("email", email); put("password", password) }, requiresSession = false)

    fun loginJellyfin(username: String, password: String) =
        Route("POST", "/auth/jellyfin", buildJsonObject { put("username", username); put("password", password) }, requiresSession = false)

    fun loginPlex(authToken: String) =
        Route("POST", "/auth/plex", buildJsonObject { put("authToken", authToken) }, requiresSession = false)

    // Search and discover. TMDB-backed, so paged by `page`.
    fun search(query: String, page: Int) = Route("GET", "/search", query = listOf("query" to query, "page" to "$page"))
    fun discoverTrending(page: Int) = paged("/discover/trending", page)
    fun discoverMovies(page: Int) = paged("/discover/movies", page)
    fun discoverMoviesUpcoming(page: Int) = paged("/discover/movies/upcoming", page)
    fun discoverTv(page: Int) = paged("/discover/tv", page)
    fun discoverTvUpcoming(page: Int) = paged("/discover/tv/upcoming", page)

    private fun paged(path: String, page: Int) = Route("GET", path, query = listOf("page" to "$page"))
}
