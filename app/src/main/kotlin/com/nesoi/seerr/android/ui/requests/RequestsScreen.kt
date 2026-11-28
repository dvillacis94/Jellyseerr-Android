package com.nesoi.seerr.android.ui.requests

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.MediaFormat
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.media.TmdbImage
import com.nesoi.seerr.android.requests.RequestAction
import com.nesoi.seerr.android.requests.RequestDisplay
import com.nesoi.seerr.android.requests.RequestFilter
import com.nesoi.seerr.android.requests.RequestRow
import com.nesoi.seerr.android.requests.RequestSort
import com.nesoi.seerr.android.requests.RequestsModel
import com.nesoi.seerr.android.ui.media.ErrorMessage
import com.nesoi.seerr.android.ui.media.PosterImage
import kotlinx.coroutines.launch

/** The Requests tab. [onOpen] goes to a title's page, given its route. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(model: RequestsModel, onOpen: (String) -> Unit) {
    val filter by model.filter.collectAsStateWithLifecycle()
    val sort by model.sort.collectAsStateWithLifecycle()
    val list by model.list.collectAsStateWithLifecycle()
    val state by list.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val actionError by model.actionError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var reviewing by remember { mutableStateOf<Int?>(null) }
    var confirmDelete by remember { mutableStateOf<RequestRow?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(list) { list.loadIfNeeded() }
    LaunchedEffect(list, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { list.loadMoreIfNeeded(it) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_requests), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            SortMenu(sort, model::setSort)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RequestFilter.entries) { option ->
                FilterChip(selected = option == filter, onClick = { model.setFilter(option) }, label = { Text(stringResource(option.label)) })
            }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; try { list.reload() } finally { refreshing = false } } },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isFailed -> ErrorMessage(state.error, onRetry = { scope.launch { list.retry() } })
                !state.hasLoaded -> Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.requests_empty),
                        Modifier.align(Alignment.Center).padding(32.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(state.items, key = { it.id }) { row ->
                        RequestRowView(
                            row = row,
                            actions = model.actionsFor(row).filter { it == RequestAction.Approve || it == RequestAction.Decline },
                            busy = row.id in busy,
                            onClick = { reviewing = row.id },
                            onAction = { model.perform(it, row) },
                        )
                    }
                    if (state.isLoading) {
                        item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                }
            }
        }
    }

    // The row is looked up again each time, so the sheet always shows what the list holds now.
    val open = reviewing?.let { id -> state.items.firstOrNull { it.id == id } }
    if (reviewing != null && open == null) reviewing = null
    open?.let { row ->
        ReviewSheet(
            row = row,
            actions = model.actionsFor(row),
            busy = row.id in busy,
            onDismiss = { reviewing = null },
            onAction = { action ->
                if (action == RequestAction.Delete) confirmDelete = row else model.perform(action, row)
            },
            onOpenTitle = row.request.titleType?.let { type -> { onOpen("${type.path}/${row.request.media.tmdbId}") } },
        )
    }

    confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.requests_delete_title)) },
            text = { Text(stringResource(R.string.requests_delete_body, row.title ?: stringResource(R.string.requests_untitled))) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = null; model.perform(RequestAction.Delete, row) { reviewing = null } }) {
                    Text(stringResource(R.string.requests_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }

    actionError?.let { error ->
        AlertDialog(
            onDismissRequest = model::dismissActionError,
            text = { Text(error.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.error_generic)) },
            confirmButton = { TextButton(onClick = model::dismissActionError) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun SortMenu(sort: RequestSort, onSort: (RequestSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.requests_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RequestSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { if (option == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { open = false; onSort(option) },
                )
            }
        }
    }
}

@Composable
private fun RequestRowView(
    row: RequestRow,
    actions: List<RequestAction>,
    busy: Boolean,
    onClick: () -> Unit,
    onAction: (RequestAction) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosterImage(TmdbImage.poster(row.posterPath), Modifier.width(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.title ?: stringResource(R.string.requests_untitled), style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(summary(row), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                row.request.requestedBy?.let {
                    Text(
                        stringResource(R.string.requests_by, it.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusChip(row.request.display)
            }
        }
        if (actions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else actions.forEach { action ->
                    if (action == RequestAction.Approve) {
                        Button(onClick = { onAction(action) }, contentPadding = ButtonPadding) {
                            Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(18.dp))
                            Text(stringResource(action.label), Modifier.padding(start = 6.dp))
                        }
                    } else {
                        OutlinedButton(onClick = { onAction(action) }, contentPadding = ButtonPadding) {
                            Icon(Icons.Filled.Close, contentDescription = null, Modifier.size(18.dp))
                            Text(stringResource(action.label), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

private val ButtonPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)

/** "Movie · 2026", or "Series · 2011 · Seasons 1, 2" for a series. */
@Composable
private fun summary(row: RequestRow): String {
    val type = when (row.request.titleType) {
        TitleType.Movie -> stringResource(R.string.type_movie)
        TitleType.Series -> stringResource(R.string.type_series)
        null -> null
    }
    val seasons = row.request.seasons.map { it.seasonNumber }.sorted().takeIf { it.isNotEmpty() }?.let {
        pluralStringResource(R.plurals.requests_seasons, it.size, it.joinToString(", "))
    }
    return listOfNotNull(type, row.year, if (row.request.is4k) "4K" else null, seasons).joinToString(" · ")
}

@Composable
private fun StatusChip(display: RequestDisplay) {
    val color = display.color
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(stringResource(display.label), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewSheet(
    row: RequestRow,
    actions: List<RequestAction>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onAction: (RequestAction) -> Unit,
    onOpenTitle: (() -> Unit)?,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val request = row.request
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PosterImage(TmdbImage.poster(row.posterPath), Modifier.width(72.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.title ?: stringResource(R.string.requests_untitled), style = MaterialTheme.typography.titleLarge)
                    Text(summary(row), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChip(request.display)
                }
            }
            val facts = buildList {
                request.requestedBy?.let { add(stringResource(R.string.requests_fact_requested_by) to it.name) }
                MediaFormat.date(request.createdAt)?.let { add(stringResource(R.string.requests_fact_requested_on) to it) }
                request.modifiedBy?.let { add(stringResource(R.string.requests_fact_handled_by) to it.name) }
                request.profileName?.let { add(stringResource(R.string.requests_fact_profile) to it) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                facts.forEach { (label, value) ->
                    Row {
                        Text(label, Modifier.width(120.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (busy) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    actions.filter { it != RequestAction.Delete }.forEach { action ->
                        if (action == RequestAction.Decline) {
                            OutlinedButton(onClick = { onAction(action) }, Modifier.fillMaxWidth()) { Text(stringResource(action.label)) }
                        } else {
                            Button(onClick = { onAction(action) }, Modifier.fillMaxWidth()) { Text(stringResource(action.label)) }
                        }
                    }
                    onOpenTitle?.let {
                        OutlinedButton(onClick = { onDismiss(); it() }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.requests_view_title)) }
                    }
                    if (RequestAction.Delete in actions) {
                        TextButton(onClick = { onAction(RequestAction.Delete) }, Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.requests_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private val RequestAction.label: Int
    get() = when (this) {
        RequestAction.Approve -> R.string.requests_approve
        RequestAction.Decline -> R.string.requests_decline
        RequestAction.Retry -> R.string.requests_retry
        RequestAction.Delete -> R.string.requests_delete
    }

private val RequestFilter.label: Int
    get() = when (this) {
        RequestFilter.All -> R.string.requests_filter_all
        RequestFilter.Pending -> R.string.status_pending
        RequestFilter.Approved -> R.string.requests_filter_approved
        RequestFilter.Processing -> R.string.status_processing
        RequestFilter.Available -> R.string.status_available
        RequestFilter.Unavailable -> R.string.requests_filter_unavailable
        RequestFilter.Failed -> R.string.requests_filter_failed
        RequestFilter.Deleted -> R.string.status_deleted
    }

private val RequestSort.label: Int
    get() = when (this) {
        RequestSort.Added -> R.string.requests_sort_added
        RequestSort.Modified -> R.string.requests_sort_modified
    }

private val RequestDisplay.label: Int
    get() = when (this) {
        RequestDisplay.Pending -> R.string.status_pending
        RequestDisplay.Processing -> R.string.status_processing
        RequestDisplay.Available -> R.string.status_available
        RequestDisplay.PartiallyAvailable -> R.string.status_partial
        RequestDisplay.Declined -> R.string.requests_declined
        RequestDisplay.Failed -> R.string.requests_filter_failed
        RequestDisplay.Completed -> R.string.requests_completed
        RequestDisplay.Deleted -> R.string.status_deleted
    }

/** The same colours the poster badges use. */
private val RequestDisplay.color: Color
    get() = when (this) {
        RequestDisplay.Pending -> Color(0xFFEAB308)
        RequestDisplay.Processing -> Color(0xFF6366F1)
        RequestDisplay.Available, RequestDisplay.PartiallyAvailable, RequestDisplay.Completed -> Color(0xFF22C55E)
        RequestDisplay.Declined, RequestDisplay.Failed -> Color(0xFFEF4444)
        RequestDisplay.Deleted -> Color(0xFF6B7280)
    }
