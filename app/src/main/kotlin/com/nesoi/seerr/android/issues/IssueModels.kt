package com.nesoi.seerr.android.issues

import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.Permission
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.requests.RequestUser
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Seerr's `IssueType`. */
enum class IssueType(val code: Int, val label: Int) {
    Video(1, R.string.issue_type_video),
    Audio(2, R.string.issue_type_audio),
    Subtitle(3, R.string.issue_type_subtitle),
    Other(4, R.string.issue_type_other);

    companion object {
        fun from(code: Int?) = entries.firstOrNull { it.code == code } ?: Other
    }
}

/** Seerr's `IssueStatus`. */
enum class IssueStatus(val code: Int, val label: Int, val path: String?) {
    Unknown(0, R.string.issue_status_unknown, null),
    Open(1, R.string.issue_status_open, "open"),
    Resolved(2, R.string.issue_status_resolved, "resolved");

    companion object {
        fun from(code: Int?) = entries.firstOrNull { it.code == code } ?: Unknown
    }
}

enum class IssueFilter(val value: String, val label: Int) {
    All("all", R.string.issues_filter_all),
    Open("open", R.string.issues_filter_open),
    Resolved("resolved", R.string.issues_filter_resolved),
}

@Serializable
data class IssueMedia(val id: Int = 0, val tmdbId: Int = 0, val mediaType: String? = null)

@Serializable
data class IssueComment(
    val id: Int,
    val message: String? = null,
    val user: RequestUser? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    /** Saving bumps `updatedAt`; allow a moment for the original insert. */
    val isEdited: Boolean
        get() {
            val created = createdAt?.let(::parseInstant) ?: return false
            val updated = updatedAt?.let(::parseInstant) ?: return false
            return updated.epochSecond - created.epochSecond > 1
        }
}

@Serializable
data class Issue(
    val id: Int,
    val issueType: Int? = null,
    val status: Int? = null,
    val problemSeason: Int? = null,
    val problemEpisode: Int? = null,
    val media: IssueMedia? = null,
    val createdBy: RequestUser? = null,
    val modifiedBy: RequestUser? = null,
    val comments: List<IssueComment> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val type: IssueType get() = IssueType.from(issueType)
    val issueStatus: IssueStatus get() = IssueStatus.from(status)

    val titleType: TitleType?
        get() = when (media?.mediaType) {
            "movie" -> TitleType.Movie
            "tv" -> TitleType.Series
            else -> null
        }

    val sortedComments: List<IssueComment> get() = comments.sortedWith(compareBy({ it.createdAt ?: "" }, { it.id }))

    /** What the reporter wrote: the server stores it as the first comment. */
    val description: IssueComment? get() = sortedComments.firstOrNull()

    /** The comments after the description. */
    val replies: List<IssueComment> get() = sortedComments.drop(1)

    /** The season, or season and episode, the problem is in. Null for the whole movie or series. */
    val affected: Pair<Int, Int?>?
        get() = problemSeason?.takeIf { it > 0 }?.let { it to problemEpisode?.takeIf { episode -> episode > 0 } }
}

@Serializable
data class IssuePage(val pageInfo: PageInfo = PageInfo(), val results: List<Issue> = emptyList()) {
    @Serializable
    data class PageInfo(val pages: Int = 0, val page: Int = 1, val results: Int = 0)
}

/** An issue with the title it is about. A title that can't be fetched leaves [title] empty. */
data class IssueRow(val issue: Issue, val title: String?, val posterPath: String?, val year: String?) {
    val id: Int get() = issue.id
}

/**
 * Who can do what with issues, following the checks in the server's `routes/issue.ts` and
 * `issueComment.ts`. Some refusals come back as 401, so actions are only offered when allowed.
 */
class IssueRules(private val user: User) {
    /** `GET /issue`: everyone's issues, or only your own with just "report issues". */
    val canViewIssues: Boolean get() = user.has(Permission.MANAGE_ISSUES, Permission.VIEW_ISSUES, Permission.CREATE_ISSUES)

    /** Whether issues other people reported are visible. */
    val canViewOthers: Boolean get() = user.has(Permission.MANAGE_ISSUES, Permission.VIEW_ISSUES)

    val canReport: Boolean get() = user.has(Permission.MANAGE_ISSUES, Permission.CREATE_ISSUES)

    val canManage: Boolean get() = user.has(Permission.MANAGE_ISSUES)

    fun isOwn(issue: Issue) = issue.createdBy?.id == user.id

    /** Commenting, resolving and reopening: managers, or the reporter. */
    fun canModify(issue: Issue) = canManage || (canReport && isOwn(issue))

    /** Managers, or the reporter while nobody else has replied. */
    fun canDelete(issue: Issue) = canManage || (canReport && isOwn(issue) && issue.comments.size <= 1)

    fun isOwn(comment: IssueComment) = comment.user?.id == user.id

    /** Only the author can edit a comment, managers included. */
    fun canEdit(comment: IssueComment) = canReport && isOwn(comment)

    /** The author or a manager. The description is left alone, like the web app: delete the issue instead. */
    fun canDelete(comment: IssueComment, issue: Issue) =
        comment.id != issue.description?.id && (canManage || (canReport && isOwn(comment)))
}

private fun parseInstant(text: String): Instant? = runCatching { Instant.parse(text) }.getOrNull()

/** "Nov 27, 2026, 9:30 PM" for the locale and time zone, or null when it can't be read. */
fun formatDateTime(iso: String?, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): String? =
    iso?.let(::parseInstant)?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(zone).format(it)
    }
