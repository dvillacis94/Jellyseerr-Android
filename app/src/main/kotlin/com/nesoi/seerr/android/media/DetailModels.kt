package com.nesoi.seerr.android.media

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Genre(val id: Int, val name: String)

@Serializable
data class CastMember(
    val id: Int,
    val name: String? = null,
    val character: String? = null,
    val profilePath: String? = null,
)

@Serializable
data class Credits(val cast: List<CastMember> = emptyList())

@Serializable
data class Company(val id: Int? = null, val name: String? = null)

/** A trailer, teaser or clip from `relatedVideos`. */
@Serializable
data class RelatedVideo(
    val url: String? = null,
    val name: String? = null,
    /** "Trailer", "Teaser", "Clip", "Featurette" and so on. */
    val type: String? = null,
    val site: String? = null,
)

/** What a movie page and a series page have in common. */
sealed interface TitleDetails {
    val id: Int
    val title: String?
    val tagline: String?
    val overview: String?
    val posterPath: String?
    val backdropPath: String?
    val voteAverage: Double?
    val genres: List<Genre>
    val credits: Credits?
    val relatedVideos: List<RelatedVideo>
    val mediaInfo: MediaInfo?

    /** Whether it is on the signed-in user's Seerr watchlist (not their Plex one). */
    val onUserWatchlist: Boolean?

    val type: TitleType

    /** Release or first-air date as `YYYY-MM-DD`. */
    val date: String?

    /** The length of a movie, or of a typical episode, in minutes. */
    val runtimeMinutes: Int?
    val status: String?

    /** The studio, or the network for a series. */
    val studio: String?
}

enum class TitleType(val path: String) { Movie("movie"), Series("tv") }

/** `GET /movie/{id}`. */
@Serializable
data class MovieDetails(
    override val id: Int,
    override val title: String? = null,
    override val tagline: String? = null,
    override val overview: String? = null,
    val releaseDate: String? = null,
    val runtime: Int? = null,
    override val genres: List<Genre> = emptyList(),
    override val posterPath: String? = null,
    override val backdropPath: String? = null,
    override val voteAverage: Double? = null,
    override val status: String? = null,
    override val credits: Credits? = null,
    val collection: CollectionRef? = null,
    val productionCompanies: List<Company> = emptyList(),
    override val relatedVideos: List<RelatedVideo> = emptyList(),
    override val mediaInfo: MediaInfo? = null,
    override val onUserWatchlist: Boolean? = null,
) : TitleDetails {
    override val type get() = TitleType.Movie
    override val date get() = releaseDate
    override val runtimeMinutes get() = runtime
    override val studio get() = productionCompanies.firstOrNull()?.name

    @Serializable
    data class CollectionRef(val id: Int, val name: String? = null)
}

@Serializable
data class TvSeason(val seasonNumber: Int, val name: String? = null, val episodeCount: Int? = null, val airDate: String? = null)

/** `GET /tv/{id}`. */
@Serializable
data class TvDetails(
    override val id: Int,
    @SerialName("name") override val title: String? = null,
    override val tagline: String? = null,
    override val overview: String? = null,
    val firstAirDate: String? = null,
    val episodeRunTime: List<Int> = emptyList(),
    val numberOfSeasons: Int? = null,
    val numberOfEpisodes: Int? = null,
    val seasons: List<TvSeason> = emptyList(),
    override val genres: List<Genre> = emptyList(),
    val networks: List<Company> = emptyList(),
    override val posterPath: String? = null,
    override val backdropPath: String? = null,
    override val voteAverage: Double? = null,
    override val status: String? = null,
    override val credits: Credits? = null,
    override val relatedVideos: List<RelatedVideo> = emptyList(),
    override val mediaInfo: MediaInfo? = null,
    override val onUserWatchlist: Boolean? = null,
) : TitleDetails {
    override val type get() = TitleType.Series
    override val date get() = firstAirDate
    override val runtimeMinutes get() = episodeRunTime.firstOrNull()
    override val studio get() = networks.firstOrNull()?.name
}

/** `GET /collection/{id}`. */
@Serializable
data class CollectionDetails(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    /** The movies. Entries carry no `mediaType`, so they decode as movies. */
    val parts: List<MediaItem.Movie> = emptyList(),
)

/** An entry of `/discover/watchlist`, which carries no artwork. */
@Serializable
data class WatchlistEntry(
    val tmdbId: Int,
    val mediaType: String,
    val title: String? = null,
)

/** The title as the card for a list: what a detail page shows in a shelf of its own kind. */
fun TitleDetails.asItem(): MediaItem = when (this) {
    is MovieDetails -> MediaItem.Movie(id, title, posterPath, releaseDate, mediaInfo)
    is TvDetails -> MediaItem.Series(id, title, posterPath, firstAirDate, mediaInfo)
}

/** The YouTube trailer to offer, if there is one. */
fun TitleDetails.trailerUrl(): String? =
    relatedVideos.firstOrNull { it.type == "Trailer" && it.site == "YouTube" }
        ?.url
        ?.takeIf { it.startsWith("https://") }

/** Where a tap on this card goes, or `null` for people, who have no page yet. */
val MediaItem.detailRoute: String?
    get() = when (this) {
        is MediaItem.Movie -> "movie/$id"
        is MediaItem.Series -> "tv/$id"
        is MediaItem.Collection -> "collection/$id"
        else -> null
    }
