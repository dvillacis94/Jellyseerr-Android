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

/** Search across movies, series, people and collections, as the user types. */
class SearchModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    private val debounceMillis: Long = 350,
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
        val loader = PagedLoader { page -> api.fetch<MediaPage>(Routes.search(text, page)) }
        _results.value = loader
        loader.loadIfNeeded()
    }
}
