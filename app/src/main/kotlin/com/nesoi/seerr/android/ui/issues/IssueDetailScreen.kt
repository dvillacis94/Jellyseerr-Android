package com.nesoi.seerr.android.ui.issues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.issues.Issue
import com.nesoi.seerr.android.issues.IssueComment
import com.nesoi.seerr.android.issues.IssueDetailModel
import com.nesoi.seerr.android.issues.IssueStatus
import com.nesoi.seerr.android.issues.formatDateTime
import com.nesoi.seerr.android.media.TmdbImage
import com.nesoi.seerr.android.ui.media.ErrorMessage
import com.nesoi.seerr.android.ui.media.PosterImage
import kotlinx.coroutines.launch

/** One issue: what was reported, the replies, and replying, resolving and deleting for those allowed to. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(model: IssueDetailModel, onBack: () -> Unit, onOpenTitle: (String) -> Unit) {
    val issue by model.issue.collectAsStateWithLifecycle()
    val title by model.title.collectAsStateWithLifecycle()
    val loadError by model.loadError.collectAsStateWithLifecycle()
    val working by model.working.collectAsStateWithLifecycle()
    val actionError by model.actionError.collectAsStateWithLifecycle()
    val draft by model.draft.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDeleteIssue by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<IssueComment?>(null) }
    var editing by remember { mutableStateOf<IssueComment?>(null) }

    LaunchedEffect(model) { model.load() }

    val current = issue
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.issue_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back)) } },
                actions = {
                    if (current != null && (model.canChangeStatus || model.canDelete)) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.issue_more)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (model.canChangeStatus) {
                                    val resolved = current.issueStatus == IssueStatus.Resolved
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (resolved) R.string.issue_reopen else R.string.issue_resolve)) },
                                        onClick = { menu = false; model.setStatus(if (resolved) IssueStatus.Open else IssueStatus.Resolved) },
                                    )
                                }
                                if (model.canDelete) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.issue_delete), color = MaterialTheme.colorScheme.error) },
                                        onClick = { menu = false; confirmDeleteIssue = true },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current != null && model.canComment) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = model::setDraft,
                            placeholder = { Text(stringResource(R.string.issue_comment_hint)) },
                            maxLines = 4,
                            enabled = !working,
                            modifier = Modifier.weight(1f),
                        )
                        if (working) {
                            CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = model::sendComment, enabled = draft.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.issue_send))
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                current != null -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosterImage(TmdbImage.poster(title?.posterPath), Modifier.width(72.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title?.name ?: stringResource(R.string.requests_untitled), style = MaterialTheme.typography.titleLarge)
                            title?.year?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            model.titleRoute?.let { route -> OutlinedButton(onClick = { onOpenTitle(route) }) { Text(stringResource(R.string.issue_view_title)) } }
                        }
                    }
                    Facts(model, current)
                    current.description?.let { description ->
                        Heading(stringResource(R.string.issue_description))
                        CommentCard(description, model, onEdit = { editing = it }, onDelete = { deleting = it })
                    }
                    Heading(stringResource(R.string.issue_comments))
                    if (current.replies.isEmpty()) {
                        Text(stringResource(R.string.issue_no_comments), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    current.replies.forEach { CommentCard(it, model, onEdit = { c -> editing = c }, onDelete = { c -> deleting = c }) }
                }
                loadError != null -> ErrorMessage(loadError, onRetry = { scope.launch { model.load() } })
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }

    if (confirmDeleteIssue) {
        AlertDialog(
            onDismissRequest = { confirmDeleteIssue = false },
            title = { Text(stringResource(R.string.issue_delete_title)) },
            text = { Text(stringResource(R.string.issue_delete_message)) },
            confirmButton = {
                TextButton(onClick = { confirmDeleteIssue = false; model.delete(onDeleted = onBack) }) {
                    Text(stringResource(R.string.issue_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteIssue = false }) { Text(stringResource(R.string.filters_cancel)) } },
        )
    }
    deleting?.let { comment ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.issue_delete_comment_title)) },
            confirmButton = {
                TextButton(onClick = { deleting = null; model.deleteComment(comment) }) {
                    Text(stringResource(R.string.issue_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.filters_cancel)) } },
        )
    }
    editing?.let { comment ->
        var text by remember(comment.id) { mutableStateOf(comment.message.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.issue_edit_title)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, minLines = 3) },
            confirmButton = {
                TextButton(enabled = text.isNotBlank(), onClick = { editing = null; model.updateComment(comment, text) }) {
                    Text(stringResource(R.string.filters_confirm))
                }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.filters_cancel)) } },
        )
    }
    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = model::dismissError,
                        text = { Text(message.ifEmpty { stringResource(R.string.error_generic) }) },
            confirmButton = { TextButton(onClick = model::dismissError) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Facts(model: IssueDetailModel, issue: Issue) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Fact(stringResource(R.string.issue_problem)) { Text(stringResource(issue.type.label)) }
        if (issue.titleType == com.nesoi.seerr.android.media.TitleType.Series) {
            Fact(stringResource(R.string.issue_affects)) {
                Text(affectedText(issue) ?: stringResource(R.string.issue_all_seasons))
            }
        }
        Fact(stringResource(R.string.issue_status)) { IssueStatusChip(issue.issueStatus) }
        issue.createdBy?.let { by ->
            Fact(stringResource(R.string.issue_reported_by)) { Text(if (model.rules.isOwn(issue)) stringResource(R.string.issue_you) else by.name) }
        }
        formatDateTime(issue.createdAt)?.let { Fact(stringResource(R.string.issue_reported)) { Text(it) } }
        HorizontalDivider()
    }
}

@Composable
private fun Fact(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        value()
    }
}

@Composable
private fun CommentCard(comment: IssueComment, model: IssueDetailModel, onEdit: (IssueComment) -> Unit, onDelete: (IssueComment) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val canEdit = model.canEdit(comment)
    val canDelete = model.canDelete(comment)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = if (canEdit || canDelete) 4.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(comment.user?.name ?: stringResource(R.string.issue_unknown_user), style = MaterialTheme.typography.titleSmall)
                    val when_ = formatDateTime(comment.createdAt)
                    val edited = if (comment.isEdited) stringResource(R.string.issue_edited) else null
                    Text(listOfNotNull(when_, edited).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (canEdit || canDelete) {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.issue_more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            if (canEdit) DropdownMenuItem(text = { Text(stringResource(R.string.issue_edit)) }, onClick = { menu = false; onEdit(comment) })
                            if (canDelete) DropdownMenuItem(text = { Text(stringResource(R.string.issue_delete), color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete(comment) })
                        }
                    }
                }
            }
            Text(comment.message.orEmpty(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 12.dp))
        }
    }
}
