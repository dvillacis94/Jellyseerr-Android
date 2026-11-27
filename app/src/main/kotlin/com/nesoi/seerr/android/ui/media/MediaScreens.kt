package com.nesoi.seerr.android.ui.media

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.MediaItem
import com.nesoi.seerr.android.media.MediaList
import com.nesoi.seerr.android.media.detailRoute
import com.nesoi.seerr.android.media.PagedLoader
import com.nesoi.seerr.android.media.TmdbImage
import kotlinx.coroutines.launch
import androidx.compose.material3.Button

private val CardWidth = 116.dp

/** A titled, horizontally scrolling row of posters, with a link to the full list. */
@Composable
fun MediaShelf(
    title: String,
    loader: PagedLoader,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Without one, the shelf has no "See all", such as on a title's related shelves. */
    onSeeAll: (() -> Unit)? = null,
) {
    val state by loader.state.collectAsStateWithLifecycle()
    // A shelf that came back empty, such as no upcoming titles, is left out.
    if (state.hasLoaded && state.items.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.items.isNotEmpty() && onSeeAll != null) {
                TextButton(onClick = onSeeAll) { Text(stringResource(R.string.shelf_see_all)) }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            if (state.items.isEmpty()) {
                items(6) { PosterImage(url = null, modifier = Modifier.width(CardWidth)) }
            } else {
                items(state.items, key = { it.key }) { item ->
                    MediaCard(item, Modifier.width(CardWidth), onClick = item.detailRoute?.let { { onOpen(item) } })
                }
            }
        }
    }
}

/** A full list as a grid that keeps loading as the user scrolls. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaGridScreen(list: MediaList, loader: PagedLoader, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    val state by loader.state.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(loader) { loader.loadIfNeeded() }
    LaunchedEffect(loader, grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { loader.loadMoreIfNeeded(it) }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(list.title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isFailed -> ErrorMessage(state.error, onRetry = { scope.launch { loader.retry() } })
                !state.hasLoaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.items.isEmpty() -> Text(
                    stringResource(R.string.list_empty),
                    Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(CardWidth),
                    state = grid,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.key }) { item -> MediaCard(item, onClick = item.detailRoute?.let { { onOpen(item) } }) }
                }
            }
        }
    }
}

/** Search results for the search bar's text, shown in place of the screen's own content. */
@Composable
fun SearchResults(loader: PagedLoader, query: String, onOpen: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val state by loader.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(loader, list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { loader.loadMoreIfNeeded(it) }
    }
    Box(modifier.fillMaxSize()) {
        when {
            state.isFailed -> ErrorMessage(state.error, onRetry = { scope.launch { loader.retry() } })
            !state.hasLoaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.items.isEmpty() -> Text(
                stringResource(R.string.discover_no_results, query),
                Modifier.align(Alignment.Center).padding(32.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            else -> LazyColumn(state = list, contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.items, key = { it.key }) { item -> SearchResultRow(item, onClick = { onOpen(item) }) }
                if (state.isLoading) {
                    item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(item: MediaItem, onClick: () -> Unit) {
    // People have no page yet, so their rows aren't tappable.
    val tappable = item.detailRoute != null
    Row(
        Modifier.fillMaxWidth().let { if (tappable) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PosterImage(TmdbImage.poster(item.posterPath), Modifier.width(52.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title ?: stringResource(R.string.list_untitled), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Text(
                listOfNotNull(stringResource(item.typeName), item.year).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.mediaInfo?.mediaStatus?.takeIf { it.isNoteworthy }?.let {
                Text(stringResource(it.label), style = MaterialTheme.typography.labelMedium, color = it.color)
            }
        }
    }
}

private val MediaItem.typeName: Int
    get() = when (this) {
        is MediaItem.Movie -> R.string.type_movie
        is MediaItem.Series -> R.string.type_series
        is MediaItem.Collection -> R.string.type_collection
        else -> R.string.type_person
    }

/** The first load failed: say so, with a way to try again. */
@Composable
fun ErrorMessage(error: Throwable?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            error?.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.error_generic),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry) { Text(stringResource(R.string.error_try_again)) }
    }
}
