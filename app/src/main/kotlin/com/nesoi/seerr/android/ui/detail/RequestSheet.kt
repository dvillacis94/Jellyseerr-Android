package com.nesoi.seerr.android.ui.detail

import androidx.compose.foundation.clickable
import com.nesoi.seerr.android.ui.browse.ValueRow
import com.nesoi.seerr.android.ui.browse.SearchPickerDialog
import com.nesoi.seerr.android.ui.browse.DropdownRow
import com.nesoi.seerr.android.media.TvDetails
import com.nesoi.seerr.android.media.ServiceServer
import com.nesoi.seerr.android.media.ServiceDetails
import com.nesoi.seerr.android.media.RequestDraft
import com.nesoi.seerr.android.media.AdvancedRequestOptions
import android.text.format.Formatter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
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
    val advancedOptions by model.advanced.collectAsStateWithLifecycle()
    // Watched so the sheet redraws when the people to choose from have loaded.
    val users by model.users.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.loadQuota(); model.loadUsers() }

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

            if (users.isNotEmpty() && model.canChooseUser(draft)) RequestAs(model, draft)

            if (model.details.type == TitleType.Series) Seasons(model)

            advancedOptions?.let { Destination(it, isAnime = (model.details as? TvDetails)?.isAnime == true) }

            val quota = model.quotaFor(draft) ?: if (model.details.type == TitleType.Movie) draft.quota?.movie else draft.quota?.tv
            QuotaLine(quota, model.details.type)
            if (model.canBypassQuota(draft)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.request_ignore_quota), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = draft.ignoreQuota, onCheckedChange = model::setIgnoreQuota)
                }
            }

            val blocker = model.blocker(draft)
            val other = model.requestUserName(draft)
            blocker?.let {
                Text(
                    when {
                        it == Blocker.QuotaUsedUp && other != null -> stringResource(R.string.request_quota_used_up_other, other)
                        it is Blocker.TooManySeasons && other != null -> stringResource(R.string.request_too_many_seasons_other, other, it.remaining)
                        else -> stringResource(
                            when (it) {
                                Blocker.QuotaUsedUp -> R.string.request_quota_used_up
                                Blocker.NoSeasons -> R.string.request_pick_seasons
                                is Blocker.TooManySeasons -> R.string.request_too_many_seasons
                            },
                            (it as? Blocker.TooManySeasons)?.remaining ?: 0,
                        )
                    },
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

/** "Request as": for an admin to ask on someone else's behalf. */
@Composable
private fun RequestAs(model: RequestModel, draft: RequestDraft) {
    var picking by remember { mutableStateOf(false) }
    val users = model.eligibleUsers(draft)
    val current = users.firstOrNull { it.id == draft.requestUserId }
    ValueRow(
        stringResource(R.string.request_as),
        current?.displayNameOrEmail ?: stringResource(R.string.request_as_you),
        onClick = { picking = true },
        chevron = true,
    )
    if (picking) {
        SearchPickerDialog(
            title = stringResource(R.string.request_as_title),
            options = users.map { "${it.id}" to it.displayNameOrEmail },
            selected = "${draft.requestUserId}",
            onPick = { id -> id?.toIntOrNull()?.let(model::setRequestUser) },
            onDismiss = { picking = false },
            allowAny = false,
        )
    }
}

/** Where the request goes: the Radarr or Sonarr instance, quality, folder, language and tags. */
@Composable
private fun Destination(options: AdvancedRequestOptions, isAnime: Boolean) {
    val state by options.state.collectAsStateWithLifecycle()
    LaunchedEffect(options) { options.load() }
    val details = state.details
    when {
        state.error != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DestinationTitle()
            Text(stringResource(R.string.request_destination_failed), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        !state.loaded || (details == null && state.loading) -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            DestinationTitle()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.request_destination_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.hasChoices -> Column {
            DestinationTitle()
            if (state.servers.size > 1) {
                DropdownRow(
                    stringResource(R.string.request_dest_server),
                    state.servers.firstOrNull { it.id == state.serverId }?.let { serverLabel(it) } ?: "",
                    state.servers.map { it.id to serverLabel(it) },
                ) { options.selectServer(it) }
            }
            if (details != null) {
                val active = details.server
                if (details.profiles.size > 1) {
                    val none = stringResource(R.string.request_dest_default)
                    DropdownRow(
                        stringResource(R.string.request_dest_quality),
                        details.profiles.firstOrNull { it.id == state.profileId }?.let { defaultLabel(it.name, it.id == active.activeProfileId) } ?: none,
                        details.profiles.map { it.id to defaultLabel(it.name, it.id == active.activeProfileId) },
                    ) { options.setProfile(it) }
                }
                if (details.rootFolders.size > 1) {
                    val none = stringResource(R.string.request_dest_default)
                    val folders = details.rootFolders.mapNotNull { f -> f.path?.let { it to folderLabel(it, f.freeSpace) } }
                    DropdownRow(
                        stringResource(R.string.request_dest_folder),
                        folders.firstOrNull { it.first == state.rootFolder }?.second ?: none,
                        folders,
                    ) { options.setRootFolder(it) }
                }
                details.languageProfiles?.takeIf { it.size > 1 }?.let { languages ->
                    DropdownRow(
                        stringResource(R.string.request_dest_language),
                        languages.firstOrNull { it.id == state.languageProfileId }?.name ?: stringResource(R.string.request_dest_default),
                        languages.map { it.id to it.name },
                    ) { options.setLanguageProfile(it) }
                }
                details.tags?.takeIf { it.isNotEmpty() }?.let { tags -> TagsRow(tags, state.tags, options::setTags) }
            } else if (state.loading) {
                CircularProgressIndicator(Modifier.padding(vertical = 8.dp).size(20.dp), strokeWidth = 2.dp)
            }
            if (isAnime && state.servers.isNotEmpty() && details != null) {
                Text(stringResource(R.string.request_destination_anime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DestinationTitle() {
    Text(stringResource(R.string.request_destination), style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun serverLabel(server: ServiceServer) = defaultLabel(server.name, server.isDefault)

@Composable
private fun defaultLabel(name: String, isDefault: Boolean) = if (isDefault) stringResource(R.string.request_dest_is_default, name) else name

@Composable
private fun folderLabel(path: String, freeSpace: Long?): String {
    if (freeSpace == null) return path
    return stringResource(R.string.request_dest_free, path, Formatter.formatFileSize(LocalContext.current, freeSpace))
}

/** Tags are chosen from a list, any number of them. */
@Composable
private fun TagsRow(tags: List<ServiceDetails.Tag>, selected: Set<Int>, onChange: (Set<Int>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val summary = tags.filter { it.id in selected }.joinToString(", ") { it.label }.ifEmpty { stringResource(R.string.request_dest_none) }
    ValueRow(stringResource(R.string.request_dest_tags), summary, onClick = { open = true }, chevron = true)
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.request_dest_tags)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    tags.forEach { tag ->
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = tag.id in selected, role = Role.Checkbox, onValueChange = { on -> onChange(if (on) selected + tag.id else selected - tag.id) }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = tag.id in selected, onCheckedChange = null)
                            Text(tag.label, Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.request_dest_done)) } },
        )
    }
}
