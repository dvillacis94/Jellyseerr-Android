package com.nesoi.seerr.android.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
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
fun BrowseScreen(model: BrowseModel, onOpen: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val search = model.search
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    val filters by model.filters.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val grid by model.grid.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }
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
            IconButton(onClick = { showFilters = true }, enabled = !searching, modifier = Modifier.padding(end = 8.dp)) {
                BadgedBox(badge = { if (filters.activeCount() > 0) Badge { Text("${filters.activeCount()}") } }) {
                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.filters_button))
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
        } else if (saved.isNotEmpty()) {
            val active = model.activeSaved(filters, saved)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(saved, key = { it.id }) { filter ->
                    FilterChip(
                        selected = filter.id == active?.id,
                        // Tapping the one that is showing turns it off.
                        onClick = { if (filter.id == active?.id) model.reset() else model.apply(filter.filters) },
                        label = { Text(filter.name) },
                    )
                }
            }
        }
        val loader = results
        if (loader != null) {
            SearchResults(loader, search.trimmedQuery, onOpen)
        } else {
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
        }
    }
    if (showFilters) FiltersSheet(model, onDismiss = { showFilters = false })
}
