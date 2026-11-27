package com.nesoi.seerr.android.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.CollectionModel
import com.nesoi.seerr.android.media.DetailState
import com.nesoi.seerr.android.media.MediaItem
import com.nesoi.seerr.android.ui.media.ErrorMessage
import com.nesoi.seerr.android.ui.media.MediaCard

/** A collection: its overview and the movies in it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(model: CollectionModel, onBack: () -> Unit, onOpen: (MediaItem) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.load() }
    val loaded = (state as? DetailState.Loaded)?.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(loaded?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                DetailState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is DetailState.Failed -> ErrorMessage(current.error, onRetry = model::retry)
                is DetailState.Loaded -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(116.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    current.value.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column { Text(overview, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.collection_movies), style = MaterialTheme.typography.titleLarge)
                    }
                    items(current.value.parts, key = { it.key }) { movie -> MediaCard(movie, onClick = { onOpen(movie) }) }
                }
            }
        }
    }
}
