package com.nesoi.seerr.android.ui.browse

import androidx.compose.foundation.layout.Column
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
        SearchField(
            query = query,
            hint = stringResource(if (isMovies) R.string.browse_search_movies else R.string.browse_search_series),
            onChange = search::setQuery,
            onSearch = { search.submit(); focus.clearFocus() },
        )
        val loader = results
        if (loader != null) {
            SearchResults(loader, search.trimmedQuery, onOpen)
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        try { model.grid.reload() } finally { refreshing = false }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                MediaGrid(model.grid, onOpen)
            }
        }
    }
}
