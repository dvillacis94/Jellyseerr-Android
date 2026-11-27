package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The Discover shelves. Each one loads, and fails, on its own. */
class DiscoverModel(private val scope: CoroutineScope, api: ApiClient) {
    val lists: List<MediaList> = listOf(
        MediaList.Trending,
        MediaList.PopularMovies,
        MediaList.UpcomingMovies,
        MediaList.PopularSeries,
        MediaList.UpcomingSeries,
    )

    val shelves: Map<MediaList, PagedLoader> = lists.associateWith { it.loader(api) }

    /** Every shelf failed to load, such as when the server went away. */
    val allFailed: Flow<Boolean> = combine(shelves.values.map { it.state }) { states -> states.all { it.isFailed } }

    val firstError: Flow<Throwable?> = combine(shelves.values.map { it.state }) { states -> states.firstNotNullOfOrNull { it.error } }

    /** Loads the shelves that haven't loaded yet. Safe to call whenever the screen appears. */
    fun load() {
        scope.launch { coroutineScope { shelves.values.map { async { it.loadIfNeeded() } }.awaitAll() } }
    }

    /** Pull to refresh. The rows stay on screen until the new ones arrive. */
    suspend fun refresh() {
        coroutineScope { shelves.values.map { async { it.reload() } }.awaitAll() }
    }

    /** Starts over after every shelf failed. */
    fun retry() {
        scope.launch { refresh() }
    }
}
