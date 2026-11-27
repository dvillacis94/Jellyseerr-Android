package com.nesoi.seerr.android.requests

import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.media.MediaStatus
import com.nesoi.seerr.android.media.RequestedSeason
import com.nesoi.seerr.android.media.TitleType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Seerr's `MediaRequestStatus`. */
enum class RequestStatus(val code: Int) {
    Pending(1), Approved(2), Declined(3), Failed(4), Completed(5);

    companion object {
        fun from(code: Int?): RequestStatus = entries.firstOrNull { it.code == code } ?: Pending
    }
}

@Serializable
data class RequestMedia(
    val tmdbId: Int = 0,
    val mediaType: String? = null,
    val status: Int? = null,
    val status4k: Int? = null,
)

@Serializable
data class RequestUser(
    val id: Int,
    val displayName: String? = null,
    val username: String? = null,
    val email: String? = null,
    val plexUsername: String? = null,
    val jellyfinUsername: String? = null,
    val avatar: String? = null,
) {
    val name: String get() = displayName ?: username ?: plexUsername ?: jellyfinUsername ?: email ?: "User $id"
}

/** One entry of `GET /request`. It carries no title or artwork: see [RequestRow]. */
@Serializable
data class MediaRequest(
    val id: Int,
    val status: Int? = null,
    val type: String? = null,
    val is4k: Boolean = false,
    val createdAt: String? = null,
    val media: RequestMedia = RequestMedia(),
    val requestedBy: RequestUser? = null,
    val modifiedBy: RequestUser? = null,
    val seasons: List<RequestedSeason> = emptyList(),
    val profileName: String? = null,
    /** Set only for people who manage requests. */
    val canRemove: Boolean = false,
) {
    val requestStatus: RequestStatus get() = RequestStatus.from(status)

    val titleType: TitleType? get() = when (type ?: media.mediaType) {
        "movie" -> TitleType.Movie
        "tv" -> TitleType.Series
        else -> null
    }

    /** How the title itself stands, in the resolution that was asked for. */
    val mediaStatus: MediaStatus get() = MediaStatus.from(if (is4k) media.status4k else media.status)

    /** What to call the request: a request that went through is only "processing" until it is in the library. */
    val display: RequestDisplay
        get() = when (requestStatus) {
            RequestStatus.Pending -> RequestDisplay.Pending
            RequestStatus.Declined -> RequestDisplay.Declined
            RequestStatus.Failed -> RequestDisplay.Failed
            RequestStatus.Completed -> RequestDisplay.Completed
            RequestStatus.Approved -> when (mediaStatus) {
                MediaStatus.Available -> RequestDisplay.Available
                MediaStatus.PartiallyAvailable -> RequestDisplay.PartiallyAvailable
                MediaStatus.Deleted -> RequestDisplay.Deleted
                else -> RequestDisplay.Processing
            }
        }
}

enum class RequestDisplay { Pending, Processing, Available, PartiallyAvailable, Declined, Failed, Completed, Deleted }

@Serializable
data class RequestPage(val pageInfo: PageInfo = PageInfo(), val results: List<MediaRequest> = emptyList()) {
    @Serializable
    data class PageInfo(val pages: Int = 0, val page: Int = 1, val results: Int = 0)
}

/** What a request row needs from `/movie/{id}` or `/tv/{id}`. */
@Serializable
internal data class TitleSummary(
    val title: String? = null,
    @SerialName("name") val name: String? = null,
    val posterPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
) {
    val displayTitle: String? get() = title ?: name
    val date: String? get() = releaseDate ?: firstAirDate
}

/** A request with the title it is for. A title that can't be fetched leaves [title] empty. */
data class RequestRow(
    val request: MediaRequest,
    val title: String?,
    val posterPath: String?,
    val year: String?,
) {
    val id: Int get() = request.id
}

/** The `filter` values of `GET /request` that are worth offering. */
enum class RequestFilter(val value: String) {
    All("all"), Pending("pending"), Approved("approved"), Processing("processing"),
    Available("available"), Unavailable("unavailable"), Failed("failed"), Deleted("deleted"),
}

enum class RequestSort(val value: String) { Added("added"), Modified("modified") }

/** Whether the person may approve, decline, retry and delete anyone's requests. */
val User.canManageRequests: Boolean get() = has(Permission.MANAGE_REQUESTS)
