package com.nesoi.seerr.android.ui.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.issues.Issue
import com.nesoi.seerr.android.issues.IssueFilter
import com.nesoi.seerr.android.issues.IssueRow
import com.nesoi.seerr.android.issues.IssueStatus
import com.nesoi.seerr.android.issues.IssuesModel
import com.nesoi.seerr.android.issues.formatDateTime
import com.nesoi.seerr.android.media.TmdbImage
import com.nesoi.seerr.android.requests.RequestSort
import com.nesoi.seerr.android.ui.media.ErrorMessage
import com.nesoi.seerr.android.ui.media.PosterImage
import kotlinx.coroutines.launch

/** Profile › Issues. [onOpen] goes to an issue's page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesScreen(model: IssuesModel, onBack: () -> Unit, onOpen: (Issue) -> Unit) {
    val filter by model.filter.collectAsStateWithLifecycle()
    val sort by model.sort.collectAsStateWithLifecycle()
    val list by model.list.collectAsStateWithLifecycle()
    val state by list.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(list) { list.loadIfNeeded() }
    LaunchedEffect(list, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { list.loadMoreIfNeeded(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.issues_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back)) } },
                actions = { SortMenu(sort, model::setSort) },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IssueFilter.entries) { option ->
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
                            stringResource(R.string.issues_empty),
                            Modifier.align(Alignment.Center).padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(state.items, key = { it.id }) { row -> IssueRowView(row, model.showsReporter) { onOpen(row.issue) } }
                        if (state.isLoading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortMenu(sort: RequestSort, onSort: (RequestSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.Sort, contentDescription = stringResource(R.string.requests_sort)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            RequestSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(if (option == RequestSort.Added) R.string.requests_sort_added else R.string.requests_sort_modified)) },
                    leadingIcon = { if (option == sort) Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = { open = false; onSort(option) },
                )
            }
        }
    }
}

@Composable
private fun IssueRowView(row: IssueRow, showsReporter: Boolean, onClick: () -> Unit) {
    val issue = row.issue
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PosterImage(TmdbImage.poster(row.posterPath), Modifier.width(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.title ?: stringResource(R.string.requests_untitled), style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Text(problemSummary(issue), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val reporter = issue.createdBy?.takeIf { showsReporter }?.name
            val by = reporter?.let { stringResource(R.string.issues_by, it) }
            Text(
                listOfNotNull(by, formatDateTime(issue.createdAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IssueStatusChip(issue.issueStatus)
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** "Season 2 · Episode 5", "Season 2", or null for the whole movie or series. */
@Composable
internal fun affectedText(issue: Issue): String? = issue.affected?.let { (season, episode) ->
    val s = stringResource(R.string.issue_season, season)
    if (episode != null) "$s · ${stringResource(R.string.issue_episode, episode)}" else s
}

/** "Video", or "Subtitle · Season 2 · Episode 5". */
@Composable
internal fun problemSummary(issue: Issue): String = listOfNotNull(stringResource(issue.type.label), affectedText(issue)).joinToString(" · ")

@Composable
internal fun IssueStatusChip(status: IssueStatus) {
    val color = if (status == IssueStatus.Resolved) Color(0xFF22C55E) else Color(0xFFEAB308)
    Box(Modifier.clip(CircleShape).background(color.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(stringResource(status.label), style = MaterialTheme.typography.labelMedium, color = color)
    }
}
