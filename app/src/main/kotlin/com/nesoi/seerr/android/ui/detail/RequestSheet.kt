package com.nesoi.seerr.android.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.QuotaStatus
import com.nesoi.seerr.android.media.RequestModel
import com.nesoi.seerr.android.media.RequestModel.Blocker
import com.nesoi.seerr.android.media.RequestOutcome
import com.nesoi.seerr.android.media.SeasonAvailability
import com.nesoi.seerr.android.media.TitleType
import kotlinx.coroutines.launch

/** Asks for a movie or series: its resolution, and for a series which seasons. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestSheet(model: RequestModel, onDismiss: () -> Unit, onRequested: () -> Unit) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val outcome by model.outcome.collectAsStateWithLifecycle()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { model.loadQuota() }

    fun close() = scope.launch { sheet.hide() }.invokeOnCompletion { onDismiss() }

    ModalBottomSheet(onDismissRequest = { if (outcome != RequestOutcome.Sending) onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val sent = outcome as? RequestOutcome.Sent
            if (sent != null) {
                Sent(model.details.title, sent.approved) { onRequested(); close() }
                return@Column
            }

            Text(model.details.title.orEmpty(), style = MaterialTheme.typography.titleLarge)

            if (model.offersBoth) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!draft.is4k, { model.set4k(false) }, label = { Text(stringResource(R.string.request_standard)) })
                    FilterChip(draft.is4k, { model.set4k(true) }, label = { Text(stringResource(R.string.request_4k)) })
                }
            } else if (draft.is4k) {
                Text(stringResource(R.string.request_4k_only), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (model.details.type == TitleType.Series) Seasons(model)

            val quota = if (model.details.type == TitleType.Movie) draft.quota?.movie else draft.quota?.tv
            QuotaLine(quota, model.details.type)

            val blocker = model.blocker(draft)
            blocker?.let {
                Text(
                    stringResource(
                        when (it) {
                            Blocker.QuotaUsedUp -> R.string.request_quota_used_up
                            Blocker.NoSeasons -> R.string.request_pick_seasons
                            is Blocker.TooManySeasons -> R.string.request_too_many_seasons
                        },
                        (it as? Blocker.TooManySeasons)?.remaining ?: 0,
                    ),
                    // Not picking anything yet is a prompt, not a mistake.
                    color = if (it == Blocker.NoSeasons) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            (outcome as? RequestOutcome.Failed)?.let { failed ->
                Text(
                    failed.error.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.error_generic),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::close, enabled = outcome != RequestOutcome.Sending) { Text(stringResource(android.R.string.cancel)) }
                Button(onClick = model::send, enabled = blocker == null && outcome != RequestOutcome.Sending) {
                    if (outcome == RequestOutcome.Sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.request_send))
                }
            }
        }
    }
}

@Composable
private fun Sent(title: String?, approved: Boolean, onDone: () -> Unit) {
    Text(stringResource(if (approved) R.string.request_approved else R.string.request_sent), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(if (approved) R.string.request_approved_body else R.string.request_sent_body, title.orEmpty()),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.request_done)) }
}

@Composable
private fun Seasons(model: RequestModel) {
    val draft by model.draft.collectAsStateWithLifecycle()
    val choices = model.seasonChoices(draft.is4k)
    val open = choices.count { it.availability == SeasonAvailability.Requestable }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.request_seasons), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (model.partialAllowed && open > 0) {
            val all = draft.seasons.size == open
            TextButton(onClick = { if (all) model.clearSeasons() else model.selectAll() }) {
                Text(stringResource(if (all) R.string.request_select_none else R.string.request_select_all))
            }
        }
    }
    choices.forEach { choice ->
        val requestable = choice.availability == SeasonAvailability.Requestable
        val number = choice.season.seasonNumber
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = number in draft.seasons,
                enabled = requestable && model.partialAllowed,
                role = Role.Checkbox,
                onValueChange = { model.toggle(number) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = number in draft.seasons, onCheckedChange = null, enabled = requestable && model.partialAllowed)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    choice.season.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.request_season_n, number),
                    color = if (requestable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                choice.season.episodeCount?.let {
                    Text(pluralStringResource(R.plurals.request_episodes, it, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (choice.availability) {
                SeasonAvailability.Available -> Text(stringResource(R.string.status_available), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SeasonAvailability.Requested -> Text(stringResource(R.string.request_already_requested), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SeasonAvailability.Requestable -> Unit
            }
        }
    }
}

/** "3 of 5 movie requests left this week", when the user has a limit. */
@Composable
private fun QuotaLine(quota: QuotaStatus?, type: TitleType) {
    if (quota == null || !quota.hasLimit) return
    val remaining = quota.remaining ?: 0
    val limit = quota.limit ?: 0
    val days = quota.days ?: 0
    val kind = if (type == TitleType.Movie) R.plurals.request_quota_movies else R.plurals.request_quota_seasons
    val left = pluralStringResource(kind, limit, remaining, limit)
    Text(
        if (days > 0) "$left " + pluralStringResource(R.plurals.request_quota_days, days, days) else left,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
