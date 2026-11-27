package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** What the signed-in app keeps while the user moves between tabs. A new one is made at each sign-in. */
class SignedInModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    /** Whether the user can add to and remove from the watchlist. */
    val canEditWatchlist: Boolean = true,
) {
    val watchlist = WatchlistStore(api)
    val discover = DiscoverModel(scope, api)
    val search = SearchModel(scope, api)

    private val grids = mutableMapOf<MediaList, PagedLoader>()

    // Few pages are worth keeping, and each one is cheap to load again.
    private val titles = object : LinkedHashMap<String, TitleModel>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TitleModel>) = size > MAX_PAGES
    }
    private val collections = object : LinkedHashMap<Int, CollectionModel>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CollectionModel>) = size > MAX_PAGES
    }

    init {
        // A change on a detail page shows up on Discover and in the full list.
        scope.launch {
            watchlist.revision.drop(1).collect {
                launch { discover.shelves.getValue(MediaList.Watchlist).reload() }
                grids[MediaList.Watchlist]?.let { launch { it.reload() } }
            }
        }
    }

    /** The full list behind a shelf's "See all", kept so going back doesn't reload it. */
    fun grid(list: MediaList): PagedLoader = grids.getOrPut(list) { list.loader(api) }

    /** A movie or series page, kept so going back doesn't reload it. */
    fun title(type: TitleType, id: Int): TitleModel =
        titles.getOrPut("${type.path}-$id") { TitleModel(scope, api, type, id) }

    fun collection(id: Int): CollectionModel = collections.getOrPut(id) { CollectionModel(scope, api, id) }

    private companion object {
        const val MAX_PAGES = 16
    }
}
