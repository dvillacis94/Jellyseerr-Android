package com.nesoi.seerr.android.core.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
    /** [filters] are query items as built by `DiscoverFilters.queryItems`. */
    fun discoverMovies(page: Int, filters: List<Pair<String, String>> = emptyList()) = paged("/discover/movies", page, filters)
    fun discoverMoviesUpcoming(page: Int) = paged("/discover/movies/upcoming", page)
    fun discoverTv(page: Int, filters: List<Pair<String, String>> = emptyList()) = paged("/discover/tv", page, filters)
    fun discoverTvUpcoming(page: Int) = paged("/discover/tv/upcoming", page)

    // What the filters offer.
    fun genres(mediaType: String) = Route("GET", "/genres/$mediaType")
    val languages = Route("GET", "/languages")
    val regions = Route("GET", "/regions")

    /** [mediaType] is `movies` or `tv` here, unlike everywhere else. */
    fun watchProviders(mediaType: String, region: String) =
        Route("GET", "/watchproviders/$mediaType", query = listOf("watchRegion" to region))

    // Title pages. `/movie/{id}` and `/tv/{id}` include credits, videos and whether the user watchlisted it.
    fun movieDetail(id: Int) = Route("GET", "/movie/$id")
    fun movieRecommendations(id: Int, page: Int) = paged("/movie/$id/recommendations", page)
    fun movieSimilar(id: Int, page: Int) = paged("/movie/$id/similar", page)
    fun tvDetail(id: Int) = Route("GET", "/tv/$id")
    fun tvRecommendations(id: Int, page: Int) = paged("/tv/$id/recommendations", page)
    fun tvSimilar(id: Int, page: Int) = paged("/tv/$id/similar", page)
    fun collection(id: Int) = Route("GET", "/collection/$id")

    // The user's own watchlist: Seerr's, or Plex's for Plex users.
    fun discoverWatchlist(page: Int) = paged("/discover/watchlist", page)

    fun addWatchlist(tmdbId: Int, mediaType: String, title: String?) =
        Route("POST", "/watchlist", buildJsonObject {
            put("tmdbId", tmdbId)
            put("mediaType", mediaType)
            if (title != null) put("title", title)
        })

    fun removeWatchlist(tmdbId: Int, mediaType: String) =
        Route("DELETE", "/watchlist/$tmdbId", query = listOf("mediaType" to mediaType))

    // The signed-in user's settings.
    fun userGeneralSettings(userId: Int) = Route("GET", "/user/$userId/settings/main")
    fun userNotificationSettings(userId: Int) = Route("GET", "/user/$userId/settings/notifications")
    fun saveNotificationSettings(userId: Int, body: JsonObject) = Route("POST", "/user/$userId/settings/notifications", body)

    // Issues. Seerr answers some refused changes with 401, which is no reason to sign the user out.
    fun issues(skip: Int, take: Int, filter: String, sort: String) =
        Route("GET", "/issue", query = listOf("take" to "$take", "skip" to "$skip", "filter" to filter, "sort" to sort))

    fun issue(id: Int) = Route("GET", "/issue/$id")

    /** `status` is `open` or `resolved`. */
    fun setIssueStatus(id: Int, status: String) = Route("POST", "/issue/$id/$status", requiresSession = false)
    fun deleteIssue(id: Int) = Route("DELETE", "/issue/$id", requiresSession = false)

    fun addIssueComment(issueId: Int, message: String) =
        Route("POST", "/issue/$issueId/comment", buildJsonObject { put("message", message) }, requiresSession = false)

    fun updateIssueComment(id: Int, message: String) =
        Route("PUT", "/issueComment/$id", buildJsonObject { put("message", message) }, requiresSession = false)

    fun deleteIssueComment(id: Int) = Route("DELETE", "/issueComment/$id", requiresSession = false)

    // Requests.
    fun userQuota(userId: Int) = Route("GET", "/user/$userId/quota")

    /** `seasons` is null for a movie. */
    fun createRequest(mediaType: String, mediaId: Int, is4k: Boolean, seasons: List<Int>?) =
        Route("POST", "/request", buildJsonObject {
            put("mediaType", mediaType)
            put("mediaId", mediaId)
            put("is4k", is4k)
            if (seasons != null) put("seasons", JsonArray(seasons.map { JsonPrimitive(it) }))
        })

    /** The requests list pages by offset, not page number. */
    fun requests(skip: Int, take: Int, filter: String, sort: String) =
        Route("GET", "/request", query = listOf("take" to "$take", "skip" to "$skip", "filter" to filter, "sort" to sort, "sortDirection" to "desc"))

    /** `action` is `approve`, `decline` or `retry`. */
    fun requestAction(id: Int, action: String) = Route("POST", "/request/$id/$action")

    // Seerr answers 401, not 403, when someone may not delete a request. That is no reason to
    // send them back to sign-in.
    fun deleteRequest(id: Int) = Route("DELETE", "/request/$id", requiresSession = false)

    private fun paged(path: String, page: Int, more: List<Pair<String, String>> = emptyList()) =
        Route("GET", path, query = listOf("page" to "$page") + more)
}
