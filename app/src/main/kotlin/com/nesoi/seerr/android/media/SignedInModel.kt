package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import com.nesoi.seerr.android.notifications.NotificationSettingsModel
import com.nesoi.seerr.android.requests.RequestsModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** What the signed-in app keeps while the user moves between tabs. A new one is made at each sign-in. */
class SignedInModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    val user: User,
    filterStorage: PreferenceStorage = MemoryPreferences(),
    /** Tells one server and user's saved filters from another's. */
    storageScope: String = "local",
) {
    /** Whether the user can add to and remove from the watchlist. */
    val canEditWatchlist: Boolean get() = user.canEditWatchlist

    /** What the server allows everyone, such as 4K. Null until it has answered. */
    private val _settings = MutableStateFlow<PublicSettings?>(null)
    val settings: StateFlow<PublicSettings?> = _settings.asStateFlow()

    val watchlist = WatchlistStore(api)
    val discover = DiscoverModel(scope, api)
    val search = SearchModel(scope, api)
    val requests = RequestsModel(scope, api, user)
    val notifications = NotificationSettingsModel(api, user) { settings.value }
    private val region: () -> String? = { _settings.value?.streamingRegion?.takeIf { it.isNotEmpty() } ?: _settings.value?.discoverRegion?.takeIf { it.isNotEmpty() } }
    val movies = BrowseModel(scope, api, TitleType.Movie, filterStorage, "filters.$storageScope.${TitleType.Movie.path}", region)
    val series = BrowseModel(scope, api, TitleType.Series, filterStorage, "filters.$storageScope.${TitleType.Series.path}", region)

    private val grids = mutableMapOf<MediaList, PagedLoader>()

    // Few pages are worth keeping, and each one is cheap to load again.
    private val titles = object : LinkedHashMap<String, TitleModel>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TitleModel>) = size > MAX_PAGES
    }
    private val collections = object : LinkedHashMap<Int, CollectionModel>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, CollectionModel>) = size > MAX_PAGES
    }

    init {
        scope.launch {
            try {
                _settings.value = api.fetch<PublicSettings>(Routes.publicSettings)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Without it 4K just isn't offered, and a refresh of a page tries again.
            }
        }
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

    /** The sheet for requesting a title. A new one each time, so it starts from the title's current state. */
    fun requestModel(details: TitleDetails) = RequestModel(scope, api, user, details, settings.value)

    fun collection(id: Int): CollectionModel = collections.getOrPut(id) { CollectionModel(scope, api, id) }

    private companion object {
        const val MAX_PAGES = 16
    }
}
