package com.nesoi.seerr.android.issues

import com.nesoi.seerr.android.core.net.ApiClient
import com.nesoi.seerr.android.core.net.Routes
import com.nesoi.seerr.android.core.net.fetch
import com.nesoi.seerr.android.media.MediaFormat
import com.nesoi.seerr.android.media.TitleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What an issue's page needs about its title. */
data class IssueTitle(val name: String?, val posterPath: String?, val year: String?)

/**
 * One issue: its description and comments, with replying, editing, resolving, reopening and
 * deleting for the people allowed to. [onChanged] runs after each change so the list can reload.
 */
class IssueDetailModel(
    private val scope: CoroutineScope,
    private val api: ApiClient,
    val issueId: Int,
    val rules: IssueRules,
    /** What the list already has, shown while the full issue loads. */
    initial: Issue? = null,
    private val onChanged: suspend () -> Unit = {},
) {
    private val _issue = MutableStateFlow(initial)
    val issue: StateFlow<Issue?> = _issue.asStateFlow()

    private val _title = MutableStateFlow<IssueTitle?>(null)
    val title: StateFlow<IssueTitle?> = _title.asStateFlow()

    private val _loadError = MutableStateFlow<Throwable?>(null)
    val loadError: StateFlow<Throwable?> = _loadError.asStateFlow()

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    /** The message of the last change that failed, until it is dismissed. */
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    fun setDraft(text: String) {
        _draft.value = text
    }

    fun dismissError() {
        _actionError.value = null
    }

    val canComment: Boolean get() = _issue.value?.let(rules::canModify) == true
    val canChangeStatus: Boolean get() = canComment
    val canDelete: Boolean get() = _issue.value?.let(rules::canDelete) == true

    fun canEdit(comment: IssueComment) = rules.canEdit(comment)
    fun canDelete(comment: IssueComment) = _issue.value?.let { rules.canDelete(comment, it) } == true

    suspend fun load() {
        try {
            val loaded = api.fetch<Issue>(Routes.issue(issueId))
            _issue.value = loaded
            _loadError.value = null
            if (_title.value == null) {
                summary(api, loaded)?.let { _title.value = IssueTitle(it.displayTitle, it.posterPath, MediaFormat.year(it.date)) }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _loadError.value = error
        }
    }

    /** Posts the draft as a comment, clearing it when that worked. */
    fun sendComment() {
        val message = _draft.value.trim()
        if (!canComment || message.isEmpty()) return
        change { api.requestUnit(Routes.addIssueComment(issueId, message)); _draft.value = "" }
    }

    fun updateComment(comment: IssueComment, message: String) {
        val text = message.trim()
        if (!canEdit(comment) || text.isEmpty() || text == comment.message) return
        change { api.requestUnit(Routes.updateIssueComment(comment.id, text)) }
    }

    fun deleteComment(comment: IssueComment) {
        if (!canDelete(comment)) return
        change { api.requestUnit(Routes.deleteIssueComment(comment.id)) }
    }

    fun setStatus(status: IssueStatus) {
        val path = status.path ?: return
        if (!canChangeStatus || status == _issue.value?.issueStatus) return
        change { api.requestUnit(Routes.setIssueStatus(issueId, path)) }
    }

    /** Deletes the issue, then calls [onDeleted]. */
    fun delete(onDeleted: () -> Unit) {
        if (!canDelete || _working.value) return
        _working.value = true
        scope.launch {
            try {
                api.requestUnit(Routes.deleteIssue(issueId))
                onChanged()
                onDeleted()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _actionError.value = error.message ?: ""
            } finally {
                _working.value = false
            }
        }
    }

    /** Runs a change, then fetches the issue again: the server's reply doesn't say who wrote each comment. */
    private fun change(action: suspend () -> Unit) {
        if (_working.value) return
        _working.value = true
        scope.launch {
            try {
                action()
                _issue.value = api.fetch<Issue>(Routes.issue(issueId))
                onChanged()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _actionError.value = error.message ?: ""
            } finally {
                _working.value = false
            }
        }
    }

    /** The route of the title's page, when it is one the app can open. */
    val titleRoute: String?
        get() {
            val issue = _issue.value ?: return null
            val type = issue.titleType ?: return null
            val tmdbId = issue.media?.tmdbId?.takeIf { it > 0 } ?: return null
            return "${type.path}/$tmdbId"
        }
}
