package com.nesoi.seerr.android.ui.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.issues.IssueType
import com.nesoi.seerr.android.issues.ReportIssueModel
import com.nesoi.seerr.android.issues.ReportOutcome

/** Report a problem with a title in the library. Closes itself once the issue has been made. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportIssueSheet(model: ReportIssueModel, onDismiss: () -> Unit) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val outcome by model.outcome.collectAsStateWithLifecycle()
    val sending = outcome == ReportOutcome.Sending
    LaunchedEffect(outcome) { if (outcome == ReportOutcome.Sent) onDismiss() }

    ModalBottomSheet(onDismissRequest = { if (!sending) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.titleLarge)

            Text(stringResource(R.string.report_whats_wrong), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                IssueType.entries.forEachIndexed { index, type ->
                    SegmentedButton(
                        selected = type == draft.type,
                        onClick = { model.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, IssueType.entries.size),
                        enabled = !sending,
                    ) { Text(stringResource(type.label), maxLines = 1) }
                }
            }

            if (model.isSeries && model.seasonOptions.isNotEmpty()) {
                Choice(
                    label = stringResource(R.string.report_season),
                    value = if (draft.season == 0) stringResource(R.string.report_all_seasons) else stringResource(R.string.issue_season, draft.season),
                    options = listOf(0 to stringResource(R.string.report_all_seasons)) + model.seasonOptions.map { it to stringResource(R.string.issue_season, it) },
                    enabled = !sending,
                    onPick = model::setSeason,
                )
                val episodes = model.episodeOptions
                if (episodes.isNotEmpty()) {
                    Choice(
                        label = stringResource(R.string.report_episode),
                        value = if (draft.episode == 0) stringResource(R.string.report_all_episodes) else stringResource(R.string.issue_episode, draft.episode),
                        options = listOf(0 to stringResource(R.string.report_all_episodes)) + episodes.map { it to stringResource(R.string.issue_episode, it) },
                        enabled = !sending,
                        onPick = model::setEpisode,
                    )
                }
            }

            OutlinedTextField(
                value = draft.message,
                onValueChange = model::setMessage,
                label = { Text(stringResource(R.string.report_details)) },
                placeholder = { Text(stringResource(R.string.report_hint)) },
                minLines = 4,
                maxLines = 10,
                enabled = !sending,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.report_footer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            (outcome as? ReportOutcome.Failed)?.let {
                Text(it.message.ifEmpty { stringResource(R.string.error_generic) }, color = MaterialTheme.colorScheme.error)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !sending) { Text(stringResource(R.string.filters_cancel)) }
                Button(onClick = model::send, enabled = model.canSubmit) {
                    if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Text(stringResource(R.string.report_submit))
                }
            }
        }
    }
}

/** A labelled value that opens a short list to choose from. */
@Composable
private fun Choice(label: String, value: String, options: List<Pair<Int, String>>, enabled: Boolean, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { open = true }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (code, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onPick(code) }) }
        }
    }
}
