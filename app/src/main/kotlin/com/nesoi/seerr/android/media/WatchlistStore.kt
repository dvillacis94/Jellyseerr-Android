package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiException
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The signed-in user's watchlist: adding and removing titles. Shared app-wide so a change on a
 * detail page shows up on Discover and in the full list.
 */
class WatchlistStore(private val api: ApiClient) {
    /** What the user set since the page loaded, overriding the page's `onUserWatchlist`. */
    private val _overrides = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val overrides: StateFlow<Map<String, Boolean>> = _overrides.asStateFlow()

    private val _updating = MutableStateFlow<Set<String>>(emptySet())
    val updating: StateFlow<Set<String>> = _updating.asStateFlow()

    /** Bumped after every change, so lists showing the watchlist know to reload. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    /** Adds or removes the title. Already being in (or out of) the list counts as success. */
    suspend fun set(isOn: Boolean, type: TitleType, tmdbId: Int, title: String?) {
        val key = key(type, tmdbId)
        if (key in _updating.value) return
        _updating.update { it + key }
        try {
            try {
                if (isOn) {
                    api.requestUnit(Routes.addWatchlist(tmdbId, type.path, title))
                } else {
                    api.requestUnit(Routes.removeWatchlist(tmdbId, type.path))
                }
            } catch (error: ApiException.Conflict) {
                // Added from somewhere else already.
                if (!isOn) throw error
            } catch (error: ApiException.NotFound) {
                // Removed from somewhere else already.
                if (isOn) throw error
            }
            _overrides.update { it + (key to isOn) }
            _revision.update { it + 1 }
        } finally {
            _updating.update { it - key }
        }
    }

    companion object {
        fun key(type: TitleType, tmdbId: Int) = "${type.path}-$tmdbId"

        /** Whether the title is on the list: what the user did here, else what the page said. */
        fun contains(overrides: Map<String, Boolean>, type: TitleType, tmdbId: Int, loaded: Boolean?): Boolean =
            overrides[key(type, tmdbId)] ?: loaded ?: false
    }
}

/**
 * A page of the watchlist with each title's details, which the list itself lacks. A title that no
 * longer resolves is left out rather than failing the page.
 */
suspend fun ApiClient.watchlistPage(page: Int): MediaPage {
    val list: DiscoverPage<WatchlistEntry> = fetch(Routes.discoverWatchlist(page))
    val items = coroutineScope {
        list.results.map { entry -> async { watchlistItem(entry) } }.awaitAll()
    }
    return DiscoverPage(list.page, list.totalPages, list.totalResults, items.filterNotNull())
}

private suspend fun ApiClient.watchlistItem(entry: WatchlistEntry): MediaItem? = try {
    when (entry.mediaType) {
        "movie" -> fetch<MovieDetails>(Routes.movieDetail(entry.tmdbId)).asItem()
        "tv" -> fetch<TvDetails>(Routes.tvDetail(entry.tmdbId)).asItem()
        else -> null
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    null
}
