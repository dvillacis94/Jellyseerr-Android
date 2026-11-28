package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The lists the filter sheet picks from: genres, languages, regions, and a region's streaming
 * services. Each is fetched the first time the sheet is opened and kept. Null means not loaded yet,
 * and a failed load is simply tried again the next time the sheet opens.
 */
class FilterOptions(private val scope: CoroutineScope, private val api: ApiClient, private val type: TitleType) {
    private val _genres = MutableStateFlow<List<Genre>?>(null)
    val genres: StateFlow<List<Genre>?> = _genres.asStateFlow()

    private val _languages = MutableStateFlow<List<Language>?>(null)
    val languages: StateFlow<List<Language>?> = _languages.asStateFlow()

    private val _regions = MutableStateFlow<List<Region>?>(null)
    val regions: StateFlow<List<Region>?> = _regions.asStateFlow()

    private val _providers = MutableStateFlow<Map<String, List<WatchProvider>>>(emptyMap())
    val providers: StateFlow<Map<String, List<WatchProvider>>> = _providers.asStateFlow()

    private val requested = mutableSetOf<String>()

    /** Just the genres, which the Movies and Series tabs need for their sections. */
    fun loadGenres() {
        if (_genres.value == null) scope.launch { attempt { _genres.value = api.fetch<List<Genre>>(Routes.genres(type.path)).sortedBy { it.name } } }
    }

    fun load() {
        loadGenres()
        if (_languages.value == null) scope.launch { attempt { _languages.value = api.fetch<List<Language>>(Routes.languages).sortedBy { it.name } } }
        if (_regions.value == null) scope.launch { attempt { _regions.value = api.fetch<List<Region>>(Routes.regions).sortedBy { it.name } } }
    }

    fun loadProviders(region: String) {
        if (region in _providers.value || !requested.add(region)) return
        scope.launch {
            try {
                val route = Routes.watchProviders(if (type == TitleType.Movie) "movies" else "tv", region)
                val list = api.fetch<List<WatchProvider>>(route).sortedWith(compareBy({ it.displayPriority }, { it.name }))
                _providers.update { it + (region to list) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                requested.remove(region)
            }
        }
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Left unloaded; opening the sheet again tries again.
        }
    }
}
