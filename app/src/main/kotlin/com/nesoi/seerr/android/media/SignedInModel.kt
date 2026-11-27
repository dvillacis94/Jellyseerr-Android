package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import kotlinx.coroutines.CoroutineScope

/** What the signed-in app keeps while the user moves between tabs. A new one is made at each sign-in. */
class SignedInModel(scope: CoroutineScope, private val api: ApiClient) {
    val discover = DiscoverModel(scope, api)
    val search = SearchModel(scope, api)

    private val grids = mutableMapOf<MediaList, PagedLoader>()

    /** The full list behind a shelf's "See all", kept so going back doesn't reload it. */
    fun grid(list: MediaList): PagedLoader = grids.getOrPut(list) { list.loader(api) }
}
