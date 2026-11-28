package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Search as the user types. Across movies, series, people and collections, or with [only] set,
 * just one kind of title: Seerr has no search for a single type, so the rest is dropped here.
 */
class SearchModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    private val debounceMillis: Long = 350,
    private val only: TitleType? = null,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Results for the current query. Null while the query is empty. */
    private val _results = MutableStateFlow<PagedLoader?>(null)
    val results: StateFlow<PagedLoader?> = _results.asStateFlow()

    private var job: Job? = null

    val trimmedQuery: String get() = _query.value.trim()

    fun setQuery(text: String) {
        if (text == _query.value) return
        _query.value = text
        job?.cancel()
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _results.value = null
            return
        }
        job = scope.launch {
            delay(debounceMillis)
            start(trimmed)
        }
    }

    /** Searches now, such as when the user taps Search on the keyboard. */
    fun submit() {
        job?.cancel()
        val trimmed = trimmedQuery
        if (trimmed.isEmpty()) {
            _results.value = null
            return
        }
        job = scope.launch { start(trimmed) }
    }

    private suspend fun start(text: String) {
        val loader = PagedLoader { page -> searchPage(text, page) }
        _results.value = loader
        loader.loadIfNeeded()
    }

    /**
     * One page of results. When the type filter empties a page there is nothing to scroll and so
     * nothing to ask for the next one, so keep going, a few pages at most, until something is left.
     * The page returned is the last one asked for, so the loader carries on from there.
     */
    private suspend fun searchPage(text: String, first: Int): MediaPage {
        var page = first
        while (true) {
            val fetched = api.fetch<MediaPage>(Routes.search(text, page))
            val type = only ?: return fetched
            val kept = fetched.results.filter { it.isOfType(type) }
            if (kept.isNotEmpty() || !fetched.hasMore || page - first >= MAX_SKIPPED_PAGES) return fetched.copy(results = kept)
            page++
        }
    }

    private fun MediaItem.isOfType(type: TitleType) = when (type) {
        TitleType.Movie -> this is MediaItem.Movie
        TitleType.Series -> this is MediaItem.Series
    }

    private companion object {
        const val MAX_SKIPPED_PAGES = 4
    }
}
