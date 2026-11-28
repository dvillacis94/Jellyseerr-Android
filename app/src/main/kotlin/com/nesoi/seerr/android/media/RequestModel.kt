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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

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
    /** The quota of whoever the request is for. */
    val quota: Quota? = null,
    /** Who the request is for: the signed-in user unless an admin chose someone else. Zero until known. */
    val requestUserId: Int = 0,
    /** Don't count the request against the quota. Only managers can. */
    val ignoreQuota: Boolean = false,
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

    /** "Request as" another user needs both permissions, as on the server. */
    val canRequestOnBehalf: Boolean get() = user.hasAll(Permission.MANAGE_USERS, Permission.MANAGE_REQUESTS)

    private val _users = MutableStateFlow<List<User>>(emptyList())

    /** Everyone, once loaded, for choosing who the request is for. */
    val users: StateFlow<List<User>> = _users.asStateFlow()

    /** The destination choices, for people allowed to make them. Rebuilt when the resolution changes. */
    private val _advanced = MutableStateFlow(makeAdvanced(initialDraft().is4k))
    val advanced: StateFlow<AdvancedRequestOptions?> = _advanced.asStateFlow()

    private fun makeAdvanced(is4k: Boolean): AdvancedRequestOptions? {
        if (!user.has(Permission.REQUEST_ADVANCED, Permission.MANAGE_REQUESTS)) return null
        val kind = if (details.type == TitleType.Movie) "radarr" else "sonarr"
        return AdvancedRequestOptions(scope, api, kind, is4k, isAnime = (details as? TvDetails)?.isAnime == true)
    }

    /** The people the request could be for at this resolution, as the server checks on create. */
    fun eligibleUsers(draft: RequestDraft): List<User> = _users.value.filter { it.canRequest(details.type, draft.is4k) }

    fun canChooseUser(draft: RequestDraft): Boolean = eligibleUsers(draft).size > 1

    fun isForSomeoneElse(draft: RequestDraft): Boolean = draft.requestUserId != user.id

    /** The requester's name, when it isn't the signed-in user. */
    fun requestUserName(draft: RequestDraft): String? =
        if (isForSomeoneElse(draft)) _users.value.firstOrNull { it.id == draft.requestUserId }?.displayNameOrEmail else null

    fun loadUsers() {
        if (!canRequestOnBehalf || _users.value.isNotEmpty()) return
        scope.launch {
            try {
                _users.value = api.fetch<UserPage>(Routes.usersForRequest()).results
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Without the list the choice just stays hidden.
            }
        }
    }

    fun setRequestUser(id: Int) {
        if (id == _draft.value.requestUserId || !canRequestOnBehalf) return
        // Their quota is not the signed-in user's, so it loads again and any bypass starts off.
        _draft.update { it.copy(requestUserId = id, quota = null, ignoreQuota = false) }
        loadQuota()
    }

    fun setIgnoreQuota(ignore: Boolean) = _draft.update { it.copy(ignoreQuota = ignore && canBypassQuota(it)) }

    /** Managers may let a request skip a limited quota. */
    fun canBypassQuota(draft: RequestDraft): Boolean = user.has(Permission.MANAGE_REQUESTS) && quotaFor(draft) != null

    private fun bypassesQuota(draft: RequestDraft) = draft.ignoreQuota && canBypassQuota(draft)

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
        return RequestDraft(is4k = is4k, seasons = defaultSeasons(is4k), requestUserId = user.id)
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
        val id = _draft.value.requestUserId
        scope.launch {
            try {
                val quota = api.fetch<Quota>(Routes.userQuota(id))
                // A reply for someone no longer chosen is ignored.
                _draft.update { if (it.requestUserId == id) it.copy(quota = quota) else it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            }
        }
    }

    fun set4k(is4k: Boolean) {
        if (is4k && !offers4k || !is4k && !offersStandard) return
        _draft.update { it.copy(is4k = is4k, seasons = defaultSeasons(is4k)) }
        _advanced.value = makeAdvanced(is4k)
        // The chosen person may not be allowed this resolution.
        val draft = _draft.value
        if (isForSomeoneElse(draft) && _users.value.isNotEmpty() && eligibleUsers(draft).none { it.id == draft.requestUserId }) setRequestUser(user.id)
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
    fun quotaFor(draft: RequestDraft): QuotaStatus? =
        (if (details.type == TitleType.Movie) draft.quota?.movie else draft.quota?.tv)?.takeIf { it.hasLimit }

    /** Why the user can't send this request, or null when they can. */
    fun blocker(draft: RequestDraft): Blocker? {
        // A manager can let the request skip the quota.
        val quota = quotaFor(draft)?.takeIf { !bypassesQuota(draft) }
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
                val route = Routes.createRequest(details.type.path, details.id, draft.is4k, seasons, extras(draft))
                val created = api.fetch<CreatedRequest>(route)
                _outcome.value = RequestOutcome.Sent(approved = created.status == REQUEST_APPROVED)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _outcome.value = RequestOutcome.Failed(error)
            }
        }
    }

    /** What only some people may set: where it goes, who it is for, and skipping the quota. */
    private fun extras(draft: RequestDraft): Map<String, JsonElement> = buildMap {
        _advanced.value?.state?.value?.destination?.let { d ->
            d.serverId?.let { put("serverId", JsonPrimitive(it)) }
            d.profileId?.let { put("profileId", JsonPrimitive(it)) }
            d.rootFolder?.let { put("rootFolder", JsonPrimitive(it)) }
            d.languageProfileId?.let { put("languageProfileId", JsonPrimitive(it)) }
            d.tags?.let { tags -> put("tags", JsonArray(tags.map { JsonPrimitive(it) })) }
        }
        if (isForSomeoneElse(draft)) put("userId", JsonPrimitive(draft.requestUserId))
        if (bypassesQuota(draft)) put("ignoreQuota", JsonPrimitive(true))
    }

    fun dismissError() {
        if (_outcome.value is RequestOutcome.Failed) _outcome.value = RequestOutcome.Idle
    }

    @Serializable
    private data class CreatedRequest(val status: Int? = null)

    @Serializable
    private data class UserPage(val results: List<User> = emptyList())

    private companion object {
        const val REQUEST_APPROVED = 2
    }
}
