package com.nesoi.seerr.android.media

import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** One kind of request quota, from `GET /user/{id}/quota`. A limit of 0 means none. */
@Serializable
data class QuotaStatus(
    val days: Int? = null,
    val limit: Int? = null,
    val used: Int? = null,
    val remaining: Int? = null,
    val restricted: Boolean = false,
) {
    val hasLimit: Boolean get() = (limit ?: 0) > 0
}

@Serializable
data class Quota(val movie: QuotaStatus = QuotaStatus(), val tv: QuotaStatus = QuotaStatus())

/** Whether a season can still be asked for. */
enum class SeasonAvailability { Requestable, Requested, Available }

data class SeasonChoice(val season: TvSeason, val availability: SeasonAvailability)

private const val STATUS_PENDING = 2
private const val STATUS_PROCESSING = 3
private const val STATUS_PARTIAL = 4
private const val STATUS_AVAILABLE = 5

/** Seerr lets someone request a title if they hold the general permission or the one for its kind. */
fun User.canRequest(type: TitleType, is4k: Boolean): Boolean = when {
    is4k && type == TitleType.Movie -> has(Permission.REQUEST_4K, Permission.REQUEST_4K_MOVIE)
    is4k -> has(Permission.REQUEST_4K, Permission.REQUEST_4K_TV)
    type == TitleType.Movie -> has(Permission.REQUEST, Permission.REQUEST_MOVIE)
    else -> has(Permission.REQUEST, Permission.REQUEST_TV)
}

fun PublicSettings?.allows4k(type: TitleType): Boolean = when (type) {
    TitleType.Movie -> this?.movie4kEnabled == true
    TitleType.Series -> this?.series4kEnabled == true
}

/** The seasons a series has, with what can still be done about each in the given resolution. */
fun TvDetails.seasonChoices(is4k: Boolean, settings: PublicSettings?): List<SeasonChoice> {
    val info = mediaInfo
    val inRequest = info?.requests.orEmpty()
        .filter { it.is4k == is4k && !it.isDeclined }
        .flatMap { it.seasons }
        .mapTo(mutableSetOf()) { it.seasonNumber }
    val specials = settings?.enableSpecialEpisodes == true
    return seasons
        .filter { it.seasonNumber > 0 || specials }
        .sortedBy { it.seasonNumber }
        .map { season ->
            val status = info?.seasons?.firstOrNull { it.seasonNumber == season.seasonNumber }
                ?.let { if (is4k) it.status4k else it.status }
            val availability = when {
                status == STATUS_AVAILABLE -> SeasonAvailability.Available
                status == STATUS_PENDING || status == STATUS_PROCESSING || status == STATUS_PARTIAL ||
                    season.seasonNumber in inRequest -> SeasonAvailability.Requested
                else -> SeasonAvailability.Requestable
            }
            SeasonChoice(season, availability)
        }
}

/** Whether anything about the title can still be requested in the given resolution. */
fun TitleDetails.requestable(is4k: Boolean, settings: PublicSettings?): Boolean {
    val status = if (is4k) mediaInfo?.mediaStatus4k else mediaInfo?.mediaStatus
    return when (this) {
        is MovieDetails -> status == null || status == MediaStatus.Unknown || status == MediaStatus.Deleted
        // A series with seasons we don't know about can still be asked for whole.
        is TvDetails -> status != MediaStatus.Blocklisted &&
            (seasons.isEmpty() || seasonChoices(is4k, settings).any { it.availability == SeasonAvailability.Requestable })
    }
}

/** Whether to offer the Request button: the user may ask, in some resolution, for something still open. */
fun TitleDetails.canBeRequestedBy(user: User, settings: PublicSettings?): Boolean {
    if (mediaInfo?.mediaStatus == MediaStatus.Blocklisted) return false
    val standard = user.canRequest(type, is4k = false) && requestable(false, settings)
    val fourK = settings.allows4k(type) && user.canRequest(type, is4k = true) && requestable(true, settings)
    return standard || fourK
}

sealed interface RequestOutcome {
    data object Idle : RequestOutcome
    data object Sending : RequestOutcome
    /** `approved` when the request went straight through, as it does for users who auto-approve. */
    data class Sent(val approved: Boolean) : RequestOutcome
    data class Failed(val error: Throwable) : RequestOutcome
}

data class RequestDraft(
    val is4k: Boolean = false,
    val seasons: Set<Int> = emptySet(),
    val quota: Quota? = null,
)

/** The state behind the request sheet for one title. */
class RequestModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    private val user: User,
    val details: TitleDetails,
    val settings: PublicSettings?,
) {
    private val _draft = MutableStateFlow(initialDraft())
    val draft: StateFlow<RequestDraft> = _draft.asStateFlow()

    private val _outcome = MutableStateFlow<RequestOutcome>(RequestOutcome.Idle)
    val outcome: StateFlow<RequestOutcome> = _outcome.asStateFlow()

    /** Whether the sheet offers a choice of resolution. */
    val offers4k: Boolean
        get() = settings.allows4k(details.type) && user.canRequest(details.type, true) && details.requestable(true, settings)

    private val offersStandard: Boolean
        get() = user.canRequest(details.type, false) && details.requestable(false, settings)

    /** Whether a choice of resolution has two answers. */
    val offersBoth: Boolean get() = offers4k && offersStandard

    val partialAllowed: Boolean get() = settings?.partialRequestsEnabled != false

    private fun initialDraft(): RequestDraft {
        // Start on 4K only when standard is no longer open.
        val is4k = !offersStandard && offers4k
        return RequestDraft(is4k = is4k, seasons = defaultSeasons(is4k))
    }

    /** With partial requests off, a series is asked for whole: everything still open. */
    private fun defaultSeasons(is4k: Boolean): Set<Int> {
        val tv = details as? TvDetails ?: return emptySet()
        val open = tv.seasonChoices(is4k, settings).filter { it.availability == SeasonAvailability.Requestable }
        return if (partialAllowed) emptySet() else open.mapTo(mutableSetOf()) { it.season.seasonNumber }
    }

    fun seasonChoices(is4k: Boolean): List<SeasonChoice> = (details as? TvDetails)?.seasonChoices(is4k, settings).orEmpty()

    /** Looks up the user's quota. A failure is fine, since the server enforces it anyway. */
    fun loadQuota() {
        scope.launch {
            try {
                val quota = api.fetch<Quota>(Routes.userQuota(user.id))
                _draft.update { it.copy(quota = quota) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    fun set4k(is4k: Boolean) {
        if (is4k && !offers4k || !is4k && !offersStandard) return
        _draft.update { it.copy(is4k = is4k, seasons = defaultSeasons(is4k)) }
    }

    fun toggle(seasonNumber: Int) {
        if (!partialAllowed) return
        _draft.update { draft ->
            if (seasonNumber in draft.seasons) draft.copy(seasons = draft.seasons - seasonNumber)
            else draft.copy(seasons = draft.seasons + seasonNumber)
        }
    }

    fun selectAll() {
        _draft.update { draft ->
            val open = seasonChoices(draft.is4k).filter { it.availability == SeasonAvailability.Requestable }
            draft.copy(seasons = open.mapTo(mutableSetOf()) { it.season.seasonNumber })
        }
    }

    fun clearSeasons() {
        if (partialAllowed) _draft.update { it.copy(seasons = emptySet()) }
    }

    /** The quota that applies to this kind of title, if it limits anything. */
    private fun quotaFor(draft: RequestDraft): QuotaStatus? =
        (if (details.type == TitleType.Movie) draft.quota?.movie else draft.quota?.tv)?.takeIf { it.hasLimit }

    /** Why the user can't send this request, or null when they can. */
    fun blocker(draft: RequestDraft): Blocker? {
        val quota = quotaFor(draft)
        if (quota?.restricted == true) return Blocker.QuotaUsedUp
        if (details is TvDetails && draft.seasons.isEmpty() && seasonChoices(draft.is4k).isNotEmpty()) return Blocker.NoSeasons
        val remaining = quota?.remaining
        if (details is TvDetails && remaining != null && draft.seasons.size > remaining) return Blocker.TooManySeasons(remaining)
        return null
    }

    sealed interface Blocker {
        data object QuotaUsedUp : Blocker
        data object NoSeasons : Blocker
        data class TooManySeasons(val remaining: Int) : Blocker
    }

    fun send() {
        val draft = _draft.value
        if (_outcome.value == RequestOutcome.Sending || blocker(draft) != null) return
        _outcome.value = RequestOutcome.Sending
        scope.launch {
            try {
                val seasons = if (details is TvDetails) draft.seasons.sorted() else null
                val route = Routes.createRequest(details.type.path, details.id, draft.is4k, seasons)
                val created = api.fetch<CreatedRequest>(route)
                _outcome.value = RequestOutcome.Sent(approved = created.status == REQUEST_APPROVED)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _outcome.value = RequestOutcome.Failed(error)
            }
        }
    }

    fun dismissError() {
        if (_outcome.value is RequestOutcome.Failed) _outcome.value = RequestOutcome.Idle
    }

    @Serializable
    private data class CreatedRequest(val status: Int? = null)

    private companion object {
        const val REQUEST_APPROVED = 2
    }
}
