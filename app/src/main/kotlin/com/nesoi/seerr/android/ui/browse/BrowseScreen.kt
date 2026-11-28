package com.nesoi.seerr.android.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import com.nesoi.seerr.android.ui.media.MediaShelf
import com.nesoi.seerr.android.media.SectionTitle
import com.nesoi.seerr.android.media.BrowseList
import com.nesoi.seerr.android.media.DiscoverFilters
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.BrowseModel
import com.nesoi.seerr.android.media.MediaItem
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.ui.media.MediaGrid
import com.nesoi.seerr.android.ui.media.SearchField
import com.nesoi.seerr.android.ui.media.SearchResults
import kotlinx.coroutines.launch

/** The Movies or Series tab: a search box over a grid of everything, or over search results. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(model: BrowseModel, onOpen: (MediaItem) -> Unit, onSeeAll: (BrowseList) -> Unit, modifier: Modifier = Modifier) {
    val search = model.search
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    val filters by model.filters.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val grid by model.grid.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showManage by remember { mutableStateOf(false) }
    // The sections show until someone asks for everything as one grid.
    var showsGrid by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var refreshing by remember { mutableStateOf(false) }
    val isMovies = model.type == TitleType.Movie

    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(if (isMovies) R.string.tab_movies else R.string.tab_series),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        val searching = query.isNotBlank()
        Row(verticalAlignment = Alignment.CenterVertically) {
            SearchField(
                query = query,
                hint = stringResource(if (isMovies) R.string.browse_search_movies else R.string.browse_search_series),
                onChange = search::setQuery,
                onSearch = { search.submit(); focus.clearFocus() },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showsGrid = !showsGrid }) {
                Icon(
                    if (showsGrid) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                    contentDescription = stringResource(if (showsGrid) R.string.browse_show_sections else R.string.browse_show_all),
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }, enabled = !searching, modifier = Modifier.padding(end = 4.dp)) {
                    BadgedBox(badge = { if (filters.activeCount() > 0) Badge { Text("${filters.activeCount()}") } }) {
                        Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.filters_button))
                    }
                }
                val active = model.activeSaved(filters, saved)
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (saved.isNotEmpty()) {
                        Text(
                            stringResource(R.string.filters_saved),
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        saved.forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.name) },
                                trailingIcon = { if (filter.id == active?.id) Icon(Icons.Filled.Check, contentDescription = null) },
                                // Choosing the one that is showing turns it off.
                                onClick = { showMenu = false; if (filter.id == active?.id) model.reset() else model.apply(filter.filters) },
                            )
                        }
                        HorizontalDivider()
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filters_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        onClick = { showMenu = false; showFilters = true },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filters_manage)) },
                        leadingIcon = { Icon(Icons.Filled.BookmarkBorder, contentDescription = null) },
                        onClick = { showMenu = false; showManage = true },
                    )
                }
            }
        }
        if (searching) {
            Text(
                stringResource(R.string.filters_during_search),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        val loader = results
        if (loader != null) {
            SearchResults(loader, search.trimmedQuery, onOpen)
        } else {
            if (!filters.isEmpty) ActiveFiltersBar(filters.activeCount(), model.activeSaved(filters, saved)?.name, onEdit = { showFilters = true }, onClear = model::reset)
            if (showsGrid) {
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = {
                        scope.launch {
                            refreshing = true
                            try { grid.reload() } finally { refreshing = false }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    MediaGrid(grid, onOpen)
                }
            } else {
                Sections(model, filters, onOpen, onSeeAll, noMatch = if (!filters.isEmpty) stringResource(if (isMovies) R.string.browse_no_match_movies else R.string.browse_no_match_series) else null)
            }
        }
    }
    if (showFilters) FiltersSheet(model, onDismiss = { showFilters = false })
    if (showManage) ManageSavedFilters(model, onDismiss = { showManage = false })
}

/** What is on, with a way to change or clear it, above the sections. */
@Composable
private fun ActiveFiltersBar(count: Int, savedName: String?, onEdit: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(onClick = onEdit).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (savedName != null) Icons.Filled.Bookmark else Icons.Filled.FilterList, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            savedName ?: if (count == 1) stringResource(R.string.browse_one_filter) else stringResource(R.string.browse_filters_count, count),
            Modifier.weight(1f).padding(horizontal = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.browse_clear_filters)) }
    }
}

/** The Movies or Series tab as sections, like Discover. Each loads when it is first shown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Sections(model: BrowseModel, filters: DiscoverFilters, onOpen: (MediaItem) -> Unit, onSeeAll: (BrowseList) -> Unit, noMatch: String?) {
    val shelves by model.shelves.collectAsStateWithLifecycle()
    val empty by model.sectionsEmpty.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    // The genre sections need the genres, fetched the first time the sections are shown.
    LaunchedEffect(model) { model.options.loadGenres() }
    // New filters make a new set of sections, which start from the top.
    val listState = rememberLazyListState()
    LaunchedEffect(filters) { listState.scrollToItem(0) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { scope.launch { refreshing = true; try { model.refreshSections() } finally { refreshing = false } } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (empty && noMatch != null) {
                item { Text(noMatch, Modifier.fillMaxWidth().padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
            }
            items(shelves, key = { it.list.key }) { shelf ->
                // Items are lazy, so this runs when the section nears the screen.
                LaunchedEffect(shelf.loader) { shelf.loader.loadIfNeeded() }
                MediaShelf(
                    title = shelf.list.title.text(),
                    loader = shelf.loader,
                    onOpen = onOpen,
                    onSeeAll = { onSeeAll(shelf.list) },
                )
            }
        }
    }
}

/** What a section is called, in the app's language. */
@Composable
fun SectionTitle.text(): String = when (this) {
    is SectionTitle.Res -> stringResource(id)
    is SectionTitle.Name -> text
}
