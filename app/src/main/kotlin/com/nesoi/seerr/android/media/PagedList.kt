package com.nesoi.seerr.android.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One load of a [PagedList]: what to show, how many rows the server sent, and whether there are more. */
data class Chunk<T>(val items: List<T>, val received: Int, val hasMore: Boolean)

data class ListState<T>(
    val items: List<T> = emptyList(),
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
 * Loads a list by offset for infinite scrolling, such as the requests. Call it from the main thread.
 *
 * The offset counts the rows the server sent, not the ones kept, so rows that are dropped
 * or repeated don't shift the next load. A cancelled load is never an error, and a reload keeps
 * the rows on screen until the new ones arrive.
 */
class PagedList<T>(
    private val key: (T) -> Any,
    private val fetch: suspend (offset: Int) -> Chunk<T>,
) {
    private val _state = MutableStateFlow(ListState<T>())
    val state: StateFlow<ListState<T>> = _state.asStateFlow()

    private var offset = 0
    private val seen = HashSet<Any>()

    suspend fun loadIfNeeded() {
        if (!_state.value.hasLoaded) loadMore()
    }

    /** Call as the row at [index] appears; fetches more when it is near the end. */
    suspend fun loadMoreIfNeeded(index: Int) {
        if (index >= _state.value.items.size - PREFETCH_DISTANCE) loadMore()
    }

    /** Tries again after a failure: the first load if there are no rows, else the next. */
    suspend fun retry() {
        if (_state.value.items.isEmpty()) reload() else loadMore()
    }

    suspend fun reload() {
        if (_state.value.isLoading) return
        _state.update { it.copy(isLoading = true) }
        try {
            val chunk = fetch(0)
            seen.clear()
            val fresh = chunk.items.filter { seen.add(key(it)) }
            offset = chunk.received
            _state.value = ListState(items = fresh, hasLoaded = true, hasMore = chunk.hasMore)
        } catch (error: CancellationException) {
            _state.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(isLoading = false, error = error, hasLoaded = true) }
        }
    }

    private suspend fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading) return
        _state.update { it.copy(isLoading = true) }
        try {
            val chunk = fetch(offset)
            val fresh = chunk.items.filter { seen.add(key(it)) }
            offset += chunk.received
            _state.update { it.copy(items = it.items + fresh, isLoading = false, error = null, hasLoaded = true, hasMore = chunk.hasMore) }
        } catch (error: CancellationException) {
            _state.update { it.copy(isLoading = false) }
            throw error
        } catch (error: Exception) {
            _state.update { it.copy(isLoading = false, error = error, hasLoaded = true) }
        }
    }

    private companion object {
        const val PREFETCH_DISTANCE = 6
    }
}
