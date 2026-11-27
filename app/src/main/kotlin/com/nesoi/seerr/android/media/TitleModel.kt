package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Route
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DetailState<out T> {
    data object Loading : DetailState<Nothing>
    data class Failed(val error: Throwable) : DetailState<Nothing>
    data class Loaded<T>(val value: T) : DetailState<T>
}

/** One movie or series page: its details, and the recommendations and similar titles under them. */
class TitleModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    val type: TitleType,
    val id: Int,
) {
    private val _state = MutableStateFlow<DetailState<TitleDetails>>(DetailState.Loading)
    val state: StateFlow<DetailState<TitleDetails>> = _state.asStateFlow()

    val recommendations = PagedLoader { page -> api.fetch<MediaPage>(recommendationsRoute(page)) }
    val similar = PagedLoader { page -> api.fetch<MediaPage>(similarRoute(page)) }

    /** Loads the page the first time it is opened. */
    fun load() {
        if (_state.value is DetailState.Loading) scope.launch { fetchDetails() }
        scope.launch { recommendations.loadIfNeeded() }
        scope.launch { similar.loadIfNeeded() }
    }

    /** Tries again after a failure. */
    fun retry() {
        _state.value = DetailState.Loading
        load()
    }

    /** Pull to refresh. What is on screen stays until the new details arrive. */
    suspend fun refresh() {
        coroutineScope {
            launch { fetchDetails() }
            launch { recommendations.reload() }
            launch { similar.reload() }
        }
    }

    private suspend fun fetchDetails() {
        try {
            val details: TitleDetails = when (type) {
                TitleType.Movie -> api.fetch<MovieDetails>(Routes.movieDetail(id))
                TitleType.Series -> api.fetch<TvDetails>(Routes.tvDetail(id))
            }
            _state.value = DetailState.Loaded(details)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // A refresh that fails keeps the page that is already showing.
            if (_state.value !is DetailState.Loaded) _state.value = DetailState.Failed(error)
        }
    }

    private fun recommendationsRoute(page: Int): Route = when (type) {
        TitleType.Movie -> Routes.movieRecommendations(id, page)
        TitleType.Series -> Routes.tvRecommendations(id, page)
    }

    private fun similarRoute(page: Int): Route = when (type) {
        TitleType.Movie -> Routes.movieSimilar(id, page)
        TitleType.Series -> Routes.tvSimilar(id, page)
    }
}

/** A collection page: its name and the movies in it. */
class CollectionModel(private val scope: CoroutineScope, private val api: ApiClient, val id: Int) {
    private val _state = MutableStateFlow<DetailState<CollectionDetails>>(DetailState.Loading)
    val state: StateFlow<DetailState<CollectionDetails>> = _state.asStateFlow()

    fun load() {
        if (_state.value is DetailState.Loading) scope.launch { fetch() }
    }

    fun retry() {
        _state.value = DetailState.Loading
        load()
    }

    private suspend fun fetch() {
        try {
            _state.value = DetailState.Loaded(api.fetch<CollectionDetails>(Routes.collection(id)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _state.value = DetailState.Failed(error)
        }
    }
}
