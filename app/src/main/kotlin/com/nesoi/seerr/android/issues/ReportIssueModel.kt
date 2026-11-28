package com.nesoi.seerr.android.issues

import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import com.nesoi.seerr.android.media.MediaStatus
import com.nesoi.seerr.android.media.TitleDetails
import com.nesoi.seerr.android.media.TvDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportDraft(
    val type: IssueType = IssueType.Video,
    /** 0 is every season. */
    val season: Int = 0,
    /** 0 is every episode of the season. */
    val episode: Int = 0,
    val message: String = "",
) {
    val trimmedMessage: String get() = message.trim()
}

sealed interface ReportOutcome {
    data object Idle : ReportOutcome
    data object Sending : ReportOutcome
    data object Sent : ReportOutcome

    /** [message] is the server's, or empty when it gave none. */
    data class Failed(val message: String) : ReportOutcome
}

/** Reporting a problem with a movie or series in the library, like the web app's "Report an issue". */
class ReportIssueModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    private val details: TitleDetails,
    /** Runs after the issue is made, so lists of issues can be fetched again. */
    private val onReported: suspend () -> Unit = {},
) {
    private val _draft = MutableStateFlow(ReportDraft())
    val draft: StateFlow<ReportDraft> = _draft.asStateFlow()

    private val _outcome = MutableStateFlow<ReportOutcome>(ReportOutcome.Idle)
    val outcome: StateFlow<ReportOutcome> = _outcome.asStateFlow()

    val isSeries: Boolean get() = details is TvDetails

    /**
     * The seasons that are in the library, or every aired one if Seerr doesn't track them. Specials
     * are left out: the server uses season 0 for "every season".
     */
    val seasonOptions: List<Int>
        get() {
            val show = details as? TvDetails ?: return emptyList()
            val inLibrary = show.mediaInfo?.seasons.orEmpty()
                .filter { MediaStatus.from(it.status).isInLibrary || MediaStatus.from(it.status4k).isInLibrary }
                .map { it.seasonNumber }
            val aired = show.seasons.filter { (it.episodeCount ?: 0) > 0 }.map { it.seasonNumber }
            return (inLibrary.ifEmpty { aired }).toSet().filter { it > 0 }.sorted()
        }

    /** The episode numbers of the chosen season. Empty for every season. */
    val episodeOptions: List<Int>
        get() {
            val show = details as? TvDetails ?: return emptyList()
            val season = _draft.value.season.takeIf { it > 0 } ?: return emptyList()
            val count = show.seasons.firstOrNull { it.seasonNumber == season }?.episodeCount?.takeIf { it > 0 } ?: return emptyList()
            return (1..count).toList()
        }

    fun setType(type: IssueType) = _draft.update { it.copy(type = type) }

    /** Choosing another season goes back to every episode. */
    fun setSeason(season: Int) = _draft.update { if (it.season == season) it else it.copy(season = season, episode = 0) }

    fun setEpisode(episode: Int) = _draft.update { it.copy(episode = episode) }

    fun setMessage(message: String) = _draft.update { it.copy(message = message) }

    val canSubmit: Boolean
        get() = details.mediaInfo?.id != null && _draft.value.trimmedMessage.isNotEmpty() && _outcome.value != ReportOutcome.Sending

    fun send() {
        val mediaId = details.mediaInfo?.id ?: return
        val draft = _draft.value
        if (!canSubmit) return
        _outcome.value = ReportOutcome.Sending
        scope.launch {
            try {
                api.fetch<Issue>(
                    Routes.createIssue(
                        issueType = draft.type.code,
                        message = draft.trimmedMessage,
                        mediaId = mediaId,
                        problemSeason = if (isSeries) draft.season else null,
                        problemEpisode = if (isSeries && draft.season > 0) draft.episode else null,
                    ),
                )
                _outcome.value = ReportOutcome.Sent
                onReported()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _outcome.value = ReportOutcome.Failed(error.message.orEmpty())
            }
        }
    }

    fun dismissError() {
        if (_outcome.value is ReportOutcome.Failed) _outcome.value = ReportOutcome.Idle
    }

    private val MediaStatus.isInLibrary: Boolean get() = this == MediaStatus.Available || this == MediaStatus.PartiallyAvailable

    private fun <T> MutableStateFlow<T>.update(change: (T) -> T) {
        value = change(value)
    }
}
