package com.nesoi.seerr.android.ui.issues

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.issues.Issue
import com.nesoi.seerr.android.issues.IssueRules
import com.nesoi.seerr.android.issues.ReportIssueModel
import com.nesoi.seerr.android.issues.canReportIssue
import com.nesoi.seerr.android.issues.formatDateTime
import com.nesoi.seerr.android.issues.openIssues
import com.nesoi.seerr.android.media.TitleDetails

/** A title's open issues, and "Report an issue" when it is in the library and the person may report. */
@Composable
fun TitleIssuesSection(
    details: TitleDetails,
    rules: IssueRules,
    newReport: (TitleDetails) -> ReportIssueModel,
    onOpenIssue: (Int) -> Unit,
) {
    val open = details.mediaInfo.openIssues(rules)
    val canReport = rules.canReport && details.mediaInfo.canReportIssue()
    if (open.isEmpty() && !canReport) return
    var reporting by remember { mutableStateOf<ReportIssueModel?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (open.isNotEmpty()) {
            Text(pluralStringResource(R.plurals.title_open_issues, open.size, open.size), style = MaterialTheme.typography.titleLarge)
            Column {
                open.forEachIndexed { index, issue ->
                    if (index > 0) HorizontalDivider()
                    IssueRow(issue, own = rules.isOwn(issue)) { onOpenIssue(issue.id) }
                }
            }
        }
        if (canReport) {
            OutlinedButton(onClick = { reporting = newReport(details) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.ReportProblem, contentDescription = null)
                Text(stringResource(R.string.report_button), Modifier.padding(start = 8.dp))
            }
        }
    }
    reporting?.let { model -> ReportIssueSheet(model, onDismiss = { reporting = null }) }
}

@Composable
private fun IssueRow(issue: Issue, own: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.title_issue_type, stringResource(issue.type.label)), style = MaterialTheme.typography.titleSmall)
            val by = issue.createdBy?.let { if (own) stringResource(R.string.title_issue_by_you) else stringResource(R.string.issues_by, it.name) }
            Text(
                listOfNotNull(by, formatDateTime(issue.createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
