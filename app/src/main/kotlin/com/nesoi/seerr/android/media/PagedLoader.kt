package com.nesoi.seerr.android.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PagedState(
    val items: List<MediaItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: Throwable? = null,
    /** True once the first load has finished, whether it worked or not. */
    val hasLoaded: Boolean = false,
    val hasMore: Boolean = true,
) {
    /** The first load failed and there is nothing to show. */
    val isFailed: Boolean get() = hasLoaded && error != null && items.isEmpty()
}

/**
 * Loads a TMDB-backed list page by page for infinite scrolling. Call it from the main thread.
 *
 * A cancelled load is never an error. A reload keeps the rows on screen until the new ones arrive.
 */
class PagedLoader(private val fetch: suspend (page: Int) -> MediaPage) {
    private val _state = MutableStateFlow(PagedState())
    val state: StateFlow<PagedState> = _state.asStateFlow()

    private var nextPage = 1
    private val seen = HashSet<String>()

    /** Loads the first page if nothing has been loaded yet. */
    suspend fun loadIfNeeded() {
        if (!_state.value.hasLoaded) loadNextPage()
    }

    /** Call as the row at [index] appears; fetches the next page when it is near the end. */
    suspend fun loadMoreIfNeeded(index: Int) {
        if (index >= _state.value.items.size - PREFETCH_DISTANCE) loadNextPage()
    }

    /** Tries again after a failure: the first page if there are no rows, else the next one. */
    suspend fun retry() {
        if (_state.value.items.isEmpty()) reload() else loadNextPage()
    }

    /** Starts over from the first page. The rows stay until the new ones arrive. */
    suspend fun reload() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true) }
        try {
            val page = fetch(1)
            seen.clear()
            val fresh = page.results.filterKeepingNew()
            nextPage = page.page + 1
            _state.value = PagedState(items = fresh, hasLoaded = true, hasMore = page.hasMore)
        } catch (error: CancellationException) {
            _state.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(isLoading = false, error = error, hasLoaded = true) }
        }
    }

    private suspend fun loadNextPage() {
        val current = _state.value
        if (!current.hasMore || current.isLoading) return
        _state.update { it.copy(isLoading = true) }
        try {
            val page = fetch(nextPage)
            // TMDB pages can repeat items as popularity shifts between requests.
            val fresh = page.results.filterKeepingNew()
            nextPage = page.page + 1
            _state.update {
                it.copy(items = it.items + fresh, isLoading = false, error = null, hasLoaded = true, hasMore = page.hasMore)
            }
        } catch (error: CancellationException) {
            _state.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(isLoading = false, error = error, hasLoaded = true) }
        }
    }

    private fun List<MediaItem>.filterKeepingNew() = filter { it !is UnsupportedItem && seen.add(it.key) }

    private companion object {
        const val PREFETCH_DISTANCE = 6
    }
}
