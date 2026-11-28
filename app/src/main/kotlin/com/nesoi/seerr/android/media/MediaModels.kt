package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.issues.Issue
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** Mirrors `MediaStatus` in Seerr's `server/constants/media.ts`. */
enum class MediaStatus(val code: Int) {
    Unknown(1), Pending(2), Processing(3), PartiallyAvailable(4), Available(5), Blocklisted(6), Deleted(7);

    /** Statuses worth a badge on a poster. `Unknown` only means "not in the library". */
    val isNoteworthy: Boolean get() = this != Unknown

    companion object {
        fun from(code: Int?): MediaStatus = entries.firstOrNull { it.code == code } ?: Unknown
    }
}

/** The library record Seerr attaches to a title it knows about (`mediaInfo`). */
@Serializable
data class MediaInfo(
    val id: Int? = null,
    val status: Int? = null,
    val status4k: Int? = null,
    /** Per season, for series. */
    val seasons: List<SeasonInfo> = emptyList(),
    /** Every request for the title, whoever made it. */
    val requests: List<RequestInfo> = emptyList(),
    /** Every issue reported on the title, whoever reported it. */
    val issues: List<Issue> = emptyList(),
) {
    val mediaStatus: MediaStatus get() = MediaStatus.from(status)
    val mediaStatus4k: MediaStatus get() = MediaStatus.from(status4k)
}

@Serializable
data class SeasonInfo(val seasonNumber: Int, val status: Int? = null, val status4k: Int? = null)

/** A request on a title. `status` is Seerr's `MediaRequestStatus`: 1 pending, 2 approved, 3 declined, 4 failed, 5 completed. */
@Serializable
data class RequestInfo(
    val id: Int,
    val status: Int? = null,
    val is4k: Boolean = false,
    val seasons: List<RequestedSeason> = emptyList(),
) {
    val isDeclined: Boolean get() = status == REQUEST_DECLINED

    private companion object {
        const val REQUEST_DECLINED = 3
    }
}

@Serializable
data class RequestedSeason(val seasonNumber: Int, val status: Int? = null)

/** One entry of `/search` and the `/discover` lists: a movie, a series, a person or a collection. */
@Serializable(with = MediaItemSerializer::class)
sealed interface MediaItem {
    val id: Int
    val title: String?
    val posterPath: String?
    val mediaInfo: MediaInfo? get() = null

    /** Release or first-air year, such as "2019". */
    val year: String? get() = null

    /** Unique across types, because TMDB ids overlap between movies, series and people. */
    val key: String get() = "${this::class.simpleName}-$id"

    @Serializable
    data class Movie(
        override val id: Int,
        override val title: String? = null,
        override val posterPath: String? = null,
        val releaseDate: String? = null,
        override val mediaInfo: MediaInfo? = null,
    ) : MediaItem {
        override val year get() = releaseDate.yearOrNull()
    }

    @Serializable
    data class Series(
        override val id: Int,
        @SerialName("name") override val title: String? = null,
        override val posterPath: String? = null,
        val firstAirDate: String? = null,
        override val mediaInfo: MediaInfo? = null,
    ) : MediaItem {
        override val year get() = firstAirDate.yearOrNull()
    }

    @Serializable
    data class Person(
        override val id: Int,
        @SerialName("name") override val title: String? = null,
        @SerialName("profilePath") override val posterPath: String? = null,
        val knownForDepartment: String? = null,
    ) : MediaItem

    @Serializable
    data class Collection(
        override val id: Int,
        override val title: String? = null,
        override val posterPath: String? = null,
    ) : MediaItem
}

private fun String?.yearOrNull(): String? = this?.takeIf { it.length >= 4 }?.substring(0, 4)

/** Picks the type from `mediaType`. Anything unrecognised is dropped rather than failing the page. */
object MediaItemSerializer : JsonContentPolymorphicSerializer<MediaItem>(MediaItem::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<MediaItem> =
        when ((element.jsonObject["mediaType"] as? JsonPrimitive)?.contentOrNull) {
            "movie", null -> MediaItem.Movie.serializer()
            "tv" -> MediaItem.Series.serializer()
            "person" -> MediaItem.Person.serializer()
            "collection" -> MediaItem.Collection.serializer()
            else -> UnsupportedItem.serializer()
        }
}

/** Stands in for a result type the app doesn't know; filtered out by [DiscoverPage]. */
@Serializable
internal data class UnsupportedItem(override val id: Int = 0) : MediaItem {
    override val title: String? get() = null
    override val posterPath: String? get() = null
}

/** TMDB-backed paging: `/search` and the `/discover` lists. */
@Serializable
data class DiscoverPage<T>(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int = 0,
    val results: List<T>,
) {
    val hasMore: Boolean get() = page < totalPages
}

typealias MediaPage = DiscoverPage<MediaItem>

internal fun MediaPage.withoutUnsupported(): MediaPage = copy(results = results.filter { it !is UnsupportedItem })

object TmdbImage {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String?): String? = path?.let { "${BASE}w342$it" }
    fun backdrop(path: String?): String? = path?.let { "${BASE}w780$it" }
    fun logo(path: String?): String? = path?.let { "${BASE}w92$it" }
    fun profile(path: String?): String? = path?.let { "${BASE}w185$it" }
}
