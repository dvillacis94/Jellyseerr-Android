package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CoroutineScope

/** The Movies or Series tab: a grid of everything, and a search that only finds that type. */
class BrowseModel(scope: CoroutineScope, api: ApiClient, val type: TitleType) {
    val grid = PagedLoader { page ->
        api.fetch<MediaPage>(
            when (type) {
                TitleType.Movie -> Routes.discoverMovies(page)
                TitleType.Series -> Routes.discoverTv(page)
            },
        )
    }

    val search = SearchModel(scope, api, only = type)
}
