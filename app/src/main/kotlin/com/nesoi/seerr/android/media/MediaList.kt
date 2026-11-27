package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Route
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch

/** A paged list of titles, shown as a shelf on Discover and as a full grid. */
enum class MediaList(val title: Int) {
    Watchlist(R.string.list_watchlist),
    Trending(R.string.list_trending),
    PopularMovies(R.string.list_popular_movies),
    UpcomingMovies(R.string.list_upcoming_movies),
    PopularSeries(R.string.list_popular_series),
    UpcomingSeries(R.string.list_upcoming_series);

    private fun route(page: Int): Route = when (this) {
        Watchlist -> error("The watchlist has its own loader")
        Trending -> Routes.discoverTrending(page)
        PopularMovies -> Routes.discoverMovies(page)
        UpcomingMovies -> Routes.discoverMoviesUpcoming(page)
        PopularSeries -> Routes.discoverTv(page)
        UpcomingSeries -> Routes.discoverTvUpcoming(page)
    }

    fun loader(api: ApiClient) = when (this) {
        Watchlist -> PagedLoader { page -> api.watchlistPage(page) }
        else -> PagedLoader { page -> api.fetch<MediaPage>(route(page)) }
    }
}
