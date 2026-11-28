package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import com.nesoi.seerr.android.core.net.seerrJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The Movies or Series tab: a grid of everything of that type, narrowed by filters, and a search
 * that only finds that type. Filters can be saved by name, and the tab reopens with the ones it
 * last had.
 */
class BrowseModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    val type: TitleType,
    private val storage: FilterStorage = MemoryFilterStorage(),
    private val storageKey: String = "filters.${type.path}",
    /** The region to start streaming services in: the user's, else the server's. */
    val defaultRegion: () -> String? = { null },
) {
    private val _filters = MutableStateFlow(DiscoverFilters())
    val filters: StateFlow<DiscoverFilters> = _filters.asStateFlow()

    private val _saved = MutableStateFlow<List<SavedFilter>>(emptyList())
    val saved: StateFlow<List<SavedFilter>> = _saved.asStateFlow()

    /** Done once what the tab remembered has been read, so the first load already uses it. */
    private val restored = CompletableDeferred<Unit>()

    /** The grid for the current filters. A change of filters replaces it. */
    private val _grid = MutableStateFlow(newGrid(null))
    val grid: StateFlow<PagedLoader> = _grid.asStateFlow()

    val search = SearchModel(scope, api, only = type)
    val options = FilterOptions(scope, api, type)

    init {
        scope.launch {
            try {
                storage.read(storageKey)?.let { text ->
                    val memory = runCatching { seerrJson.decodeFromString<FilterMemory>(text) }.getOrNull()
                    if (memory != null) {
                        _saved.value = memory.saved
                        _filters.value = memory.last
                    }
                }
            } finally {
                restored.complete(Unit)
            }
        }
    }

    /** The saved filter the tab is showing right now, if it still matches one exactly. */
    fun activeSaved(filters: DiscoverFilters, saved: List<SavedFilter>): SavedFilter? =
        saved.firstOrNull { it.filters == filters && !filters.isEmpty }

    fun apply(filters: DiscoverFilters) {
        if (filters == _filters.value) return
        _filters.value = filters
        _grid.value = newGrid(filters)
        remember()
    }

    fun reset() = apply(DiscoverFilters())

    /** Saves [filters] under [name] and shows them. */
    fun save(name: String, filters: DiscoverFilters) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _saved.value = _saved.value + SavedFilter(UUID.randomUUID().toString(), trimmed, filters)
        if (filters == _filters.value) remember() else apply(filters)
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _saved.value = _saved.value.map { if (it.id == id) it.copy(name = trimmed) else it }
        remember()
    }

    fun delete(id: String) {
        _saved.value = _saved.value.filterNot { it.id == id }
        remember()
    }

    /** Null reads the filters at fetch time, which is after they were restored. */
    private fun newGrid(filters: DiscoverFilters?) = PagedLoader { page ->
        restored.await()
        val query = (filters ?: _filters.value).queryItems(type)
        api.fetch<MediaPage>(
            when (type) {
                TitleType.Movie -> Routes.discoverMovies(page, query)
                TitleType.Series -> Routes.discoverTv(page, query)
            },
        )
    }

    private fun remember() {
        val memory = FilterMemory(_saved.value, _filters.value)
        scope.launch { storage.write(storageKey, seerrJson.encodeToString(FilterMemory.serializer(), memory)) }
    }
}
