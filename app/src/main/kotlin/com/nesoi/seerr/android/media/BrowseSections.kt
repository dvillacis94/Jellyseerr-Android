package com.nesoi.seerr.android.media

import androidx.annotation.StringRes
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.net.Route
import com.nesoi.seerr.android.core.net.Routes

/** What a section is called: one of the app's own names, or a genre's or saved filter's. */
sealed interface SectionTitle {
    data class Res(@StringRes val id: Int) : SectionTitle
    data class Name(val text: String) : SectionTitle
}

/** One section of the Movies or Series tab: a list that can open as a full grid. */
data class BrowseList(val title: SectionTitle, val kind: Kind, val filters: DiscoverFilters) {
    enum class Kind { Discover, Upcoming }

    /** Safe to put in a navigation route, and the same for the same list. */
    val key: String get() = Integer.toHexString(hashCode())

    fun route(type: TitleType, page: Int): Route = when {
        kind == Kind.Upcoming && type == TitleType.Movie -> Routes.discoverMoviesUpcoming(page)
        kind == Kind.Upcoming -> Routes.discoverTvUpcoming(page)
        type == TitleType.Movie -> Routes.discoverMovies(page, filters.queryItems(type))
        else -> Routes.discoverTv(page, filters.queryItems(type))
    }
}

/** A section with its titles, which load the first time it is shown. */
class BrowseShelf(val list: BrowseList, val loader: PagedLoader)

/** Top rated only counts titles this many people voted on, so a few votes can't top the list. */
const val TOP_RATED_MINIMUM_VOTES = 300

/**
 * The sections in order, as on iOS. Without filters: the saved filters (one each), Popular,
 * Upcoming and Top Rated. With filters: "Top results" in the chosen order and Top Rated, both
 * narrowed (Upcoming goes, as its list takes no filters, and so do the saved filters, as the
 * sections are about the filters on). Then a section per genre, which narrows the filters further,
 * so they stop once genres are picked. The same list twice, such as two saved filters that are the
 * same, is shown once.
 */
fun browseLists(type: TitleType, genres: List<Genre>, filters: DiscoverFilters, saved: List<SavedFilter>): List<BrowseList> {
    val movies = type == TitleType.Movie
    val topRated = BrowseList(
        SectionTitle.Res(if (movies) R.string.browse_top_rated_movies else R.string.browse_top_rated_series),
        BrowseList.Kind.Discover,
        filters.copy(sort = "vote_average.desc", voteCountMin = TOP_RATED_MINIMUM_VOTES),
    )
    val lists = mutableListOf<BrowseList>()
    if (!filters.isEmpty) {
        lists += BrowseList(SectionTitle.Res(R.string.browse_top_results), BrowseList.Kind.Discover, filters)
        lists += topRated
    } else {
        lists += saved.map { BrowseList(SectionTitle.Name(it.name), BrowseList.Kind.Discover, it.filters) }
        lists += BrowseList(SectionTitle.Res(if (movies) R.string.list_popular_movies else R.string.list_popular_series), BrowseList.Kind.Discover, filters)
        lists += BrowseList(SectionTitle.Res(if (movies) R.string.list_upcoming_movies else R.string.list_upcoming_series), BrowseList.Kind.Upcoming, filters)
        lists += topRated
    }
    if (filters.genres.isEmpty()) {
        genres.forEach { lists += BrowseList(SectionTitle.Name(it.name), BrowseList.Kind.Discover, filters.copy(genres = setOf(it.id))) }
    }
    return lists.distinct()
}
