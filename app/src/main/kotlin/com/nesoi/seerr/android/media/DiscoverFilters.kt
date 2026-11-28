package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.R
import kotlinx.serialization.Serializable

/** What the Movies and Series tabs can be narrowed by. Stored as JSON, so keep the names. */
@Serializable
data class DiscoverFilters(
    val sort: String = DEFAULT_SORT,
    val genres: Set<Int> = emptySet(),
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    /** An ISO 639-1 code, for the original language. */
    val language: String? = null,
    /** TMDB score, 0 to 10. Zero means any. */
    val minScore: Int = 0,
    val runtimeMin: Int? = null,
    val runtimeMax: Int? = null,
    val providers: Set<Int> = emptySet(),
    /** The ISO 3166-1 region the streaming services are for. */
    val region: String? = null,
    /** Series only: TMDB's status codes. */
    val statuses: Set<Int> = emptySet(),
    /** Only titles this many people voted on. Not something the user picks: "Top rated" sets it. */
    val voteCountMin: Int? = null,
) {
    /** How many filters are on, for the badge. The region only matters with services chosen. */
    fun activeCount(): Int = listOf(
        sort != DEFAULT_SORT,
        genres.isNotEmpty(),
        yearFrom != null || yearTo != null,
        language != null,
        minScore > 0,
        runtimeMin != null || runtimeMax != null,
        providers.isNotEmpty(),
        statuses.isNotEmpty(),
    ).count { it }

    val isEmpty: Boolean get() = activeCount() == 0

    /**
     * The query Seerr's `/discover/movies` and `/discover/tv` take. Genres must all match, so they
     * are comma-separated. Streaming services and statuses match any, so they use a bar.
     */
    fun queryItems(type: TitleType): List<Pair<String, String>> = buildList {
        if (sort != DEFAULT_SORT) add("sortBy" to sort)
        if (genres.isNotEmpty()) add("genre" to genres.sorted().joinToString(","))
        val (from, to) = if (type == TitleType.Movie) "primaryReleaseDateGte" to "primaryReleaseDateLte" else "firstAirDateGte" to "firstAirDateLte"
        yearFrom?.let { add(from to "%04d-01-01".format(it)) }
        yearTo?.let { add(to to "%04d-12-31".format(it)) }
        language?.let { add("language" to it) }
        runtimeMin?.let { add("withRuntimeGte" to "$it") }
        runtimeMax?.let { add("withRuntimeLte" to "$it") }
        if (minScore > 0) add("voteAverageGte" to "$minScore")
        if (providers.isNotEmpty()) {
            add("watchProviders" to providers.sorted().joinToString("|"))
            region?.let { add("watchRegion" to it) }
        }
        voteCountMin?.let { add("voteCountGte" to "$it") }
        if (type == TitleType.Series && statuses.isNotEmpty()) add("status" to statuses.sorted().joinToString("|"))
    }

    companion object {
        const val DEFAULT_SORT = "popularity.desc"
    }
}

/** A sort order Seerr accepts, and the label for it. */
data class SortOption(val value: String, val label: Int)

fun TitleType.sortOptions(): List<SortOption> = buildList {
    add(SortOption("popularity.desc", R.string.sort_popularity_desc))
    add(SortOption("popularity.asc", R.string.sort_popularity_asc))
    if (this@sortOptions == TitleType.Movie) {
        add(SortOption("primary_release_date.desc", R.string.sort_newest))
        add(SortOption("primary_release_date.asc", R.string.sort_oldest))
    } else {
        add(SortOption("first_air_date.desc", R.string.sort_newest))
        add(SortOption("first_air_date.asc", R.string.sort_oldest))
    }
    add(SortOption("vote_average.desc", R.string.sort_score_desc))
    add(SortOption("vote_average.asc", R.string.sort_score_asc))
    if (this@sortOptions == TitleType.Movie) {
        add(SortOption("revenue.desc", R.string.sort_revenue_desc))
        add(SortOption("revenue.asc", R.string.sort_revenue_asc))
        add(SortOption("original_title.asc", R.string.sort_title_asc))
        add(SortOption("original_title.desc", R.string.sort_title_desc))
    }
}

/** TMDB's series statuses, as Seerr's `status` filter takes them. */
val seriesStatuses = listOf(
    0 to R.string.status_tv_returning,
    1 to R.string.status_tv_planned,
    2 to R.string.status_tv_in_production,
    3 to R.string.status_tv_ended,
    4 to R.string.status_tv_cancelled,
    5 to R.string.status_tv_pilot,
)

@Serializable
data class SavedFilter(val id: String, val name: String, val filters: DiscoverFilters)

/** Everything a tab remembers about its filters. */
@Serializable
data class FilterMemory(
    val saved: List<SavedFilter> = emptyList(),
    /** What the tab showed last, so it opens the same way. */
    val last: DiscoverFilters = DiscoverFilters(),
)

/** The lookup lists the filter sheet offers. */
@Serializable
data class Language(
    @kotlinx.serialization.SerialName("iso_639_1") val code: String,
    @kotlinx.serialization.SerialName("english_name") val name: String,
)

@Serializable
data class Region(
    @kotlinx.serialization.SerialName("iso_3166_1") val code: String,
    @kotlinx.serialization.SerialName("english_name") val name: String,
)

@Serializable
data class WatchProvider(val id: Int, val name: String, val logoPath: String? = null, val displayPriority: Int = 0)
