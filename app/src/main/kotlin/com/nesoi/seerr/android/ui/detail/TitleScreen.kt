package com.nesoi.seerr.android.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.PublicSettings
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.media.RequestModel
import com.nesoi.seerr.android.media.canBeRequestedBy
import com.nesoi.seerr.android.media.CastMember
import com.nesoi.seerr.android.media.DetailState
import com.nesoi.seerr.android.media.MediaFormat
import com.nesoi.seerr.android.issues.IssuesModel
import com.nesoi.seerr.android.issues.ReportIssueModel
import com.nesoi.seerr.android.media.MediaItem
import com.nesoi.seerr.android.ui.issues.TitleIssuesSection
import com.nesoi.seerr.android.media.MediaStatus
import com.nesoi.seerr.android.media.MovieDetails
import com.nesoi.seerr.android.media.TitleDetails
import com.nesoi.seerr.android.media.TitleModel
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.media.TmdbImage
import com.nesoi.seerr.android.media.TvDetails
import com.nesoi.seerr.android.media.WatchlistStore
import com.nesoi.seerr.android.media.trailerUrl
import com.nesoi.seerr.android.ui.media.ErrorMessage
import com.nesoi.seerr.android.ui.media.MediaShelf
import com.nesoi.seerr.android.ui.media.PosterImage
import com.nesoi.seerr.android.ui.media.color
import com.nesoi.seerr.android.ui.media.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** A movie or series page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(
    model: TitleModel,
    watchlist: WatchlistStore,
    canEditWatchlist: Boolean,
    user: User,
    settings: PublicSettings?,
    newRequest: (TitleDetails) -> RequestModel,
    issues: IssuesModel,
    newReport: (TitleDetails) -> ReportIssueModel,
    onBack: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    onOpenCollection: (Int) -> Unit,
    onOpenIssue: (Int) -> Unit,
) {
    val state by model.state.collectAsStateWithLifecycle()
    // An issue reported or changed elsewhere changes what this page shows.
    val revision by issues.revision.collectAsStateWithLifecycle()
    LaunchedEffect(revision) { model.syncIssues(revision) }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    LaunchedEffect(model) { model.load() }

    Box(Modifier.fillMaxSize()) {
        when (val current = state) {
            DetailState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            is DetailState.Failed -> ErrorMessage(current.error, onRetry = model::retry)
            is DetailState.Loaded -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        try { model.refresh() } finally { refreshing = false }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                TitleContent(current.value, model, user, settings, newRequest, issues, newReport, onOpen, onOpenCollection, onOpenIssue)
            }
        }
        // The back button stays put, whatever the page is doing.
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ScrimButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back))
            }
            Box(Modifier.weight(1f))
            (state as? DetailState.Loaded)?.value?.let { details ->
                if (canEditWatchlist && details.mediaInfo?.mediaStatus != MediaStatus.Blocklisted) {
                    WatchlistButton(details, watchlist)
                }
            }
        }
    }
}

@Composable
private fun ScrimButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        content = content,
    )
}

@Composable
private fun WatchlistButton(details: TitleDetails, watchlist: WatchlistStore) {
    val overrides by watchlist.overrides.collectAsStateWithLifecycle()
    val updating by watchlist.updating.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    val isOn = WatchlistStore.contains(overrides, details.type, details.id, details.onUserWatchlist)
    val busy = WatchlistStore.key(details.type, details.id) in updating

    ScrimButton(
        onClick = {
            if (busy) return@ScrimButton
            scope.launch {
                try {
                    watchlist.set(!isOn, details.type, details.id, details.title)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed = true
                }
            }
        },
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                if (isOn) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = stringResource(if (isOn) R.string.detail_watchlist_remove else R.string.detail_watchlist_add),
            )
        }
    }
    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            text = { Text(stringResource(R.string.detail_watchlist_error)) },
            confirmButton = { TextButton(onClick = { failed = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun TitleContent(
    details: TitleDetails,
    model: TitleModel,
    user: User,
    settings: PublicSettings?,
    newRequest: (TitleDetails) -> RequestModel,
    issues: IssuesModel,
    newReport: (TitleDetails) -> ReportIssueModel,
    onOpen: (MediaItem) -> Unit,
    onOpenCollection: (Int) -> Unit,
    onOpenIssue: (Int) -> Unit,
) {
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var requesting by remember { mutableStateOf<RequestModel?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Header(details)

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (details.canBeRequestedBy(user, settings)) {
                Button(onClick = { requesting = newRequest(details) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.detail_request))
                }
            }
            details.trailerUrl()?.let { url ->
                OutlinedButton(onClick = { uri.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(stringResource(R.string.detail_trailer), Modifier.padding(start = 8.dp))
                }
            }
            Overview(details.tagline, details.overview)
            (details as? MovieDetails)?.collection?.let { collection ->
                OutlinedButton(onClick = { onOpenCollection(collection.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Collections, contentDescription = null)
                    Text(
                        stringResource(R.string.detail_part_of, collection.name ?: stringResource(R.string.detail_a_collection)),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
            Facts(facts(details))
            TitleIssuesSection(details, issues.rules, newReport, onOpenIssue)
        }

        CastShelf(details.credits?.cast.orEmpty())
        MediaShelf(stringResource(R.string.shelf_recommendations), model.recommendations, onOpen)
        MediaShelf(stringResource(R.string.shelf_similar), model.similar, onOpen)
    }

    requesting?.let { request ->
        RequestSheet(
            request,
            onDismiss = { requesting = null },
            // The page shows the title's new status once it has been fetched again.
            onRequested = { scope.launch { model.refresh() } },
        )
    }
}

/** The backdrop, with the poster, title and a line of facts under it. */
@Composable
private fun Header(details: TitleDetails) {
    val background = MaterialTheme.colorScheme.background
    Column {
        Box(Modifier.fillMaxWidth().height(220.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
            TmdbImage.backdrop(details.backdropPath)?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to background),
                ),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PosterImage(TmdbImage.poster(details.posterPath), Modifier.width(110.dp).padding(top = 0.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    details.title ?: stringResource(R.string.list_untitled),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                headline(details)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                details.voteAverage?.let { MediaFormat.rating(it) }?.let { rating ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFEAB308), modifier = Modifier.size(16.dp))
                        Text(rating, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                details.mediaInfo?.mediaStatus?.takeIf { it.isNoteworthy }?.let { status ->
                    Text(
                        stringResource(status.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.clip(CircleShape).background(status.color).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** "2019 · 2h 15m". */
private fun headline(details: TitleDetails): String? =
    listOfNotNull(MediaFormat.year(details.date), MediaFormat.runtime(details.runtimeMinutes))
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ")

@Composable
private fun Overview(tagline: String?, overview: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tagline?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        overview?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun facts(details: TitleDetails): List<Pair<String, String>> {
    val facts = mutableListOf<Pair<String, String>>()
    details.status?.let { facts += stringResource(R.string.fact_status) to it }
    when (details) {
        is MovieDetails -> {
            MediaFormat.date(details.releaseDate)?.let { facts += stringResource(R.string.fact_release_date) to it }
            MediaFormat.runtime(details.runtime)?.let { facts += stringResource(R.string.fact_runtime) to it }
        }
        is TvDetails -> {
            MediaFormat.date(details.firstAirDate)?.let { facts += stringResource(R.string.fact_first_aired) to it }
            details.numberOfSeasons?.let { facts += stringResource(R.string.fact_seasons) to "$it" }
            details.numberOfEpisodes?.let { facts += stringResource(R.string.fact_episodes) to "$it" }
            MediaFormat.runtime(details.runtimeMinutes)?.let { facts += stringResource(R.string.fact_episode_length) to it }
        }
    }
    if (details.genres.isNotEmpty()) facts += stringResource(R.string.fact_genres) to details.genres.joinToString(", ") { it.name }
    details.studio?.let {
        facts += stringResource(if (details.type == TitleType.Series) R.string.fact_network else R.string.fact_studio) to it
    }
    return facts
}

@Composable
internal fun Facts(facts: List<Pair<String, String>>) {
    if (facts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        facts.forEach { (label, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, Modifier.width(112.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CastShelf(cast: List<CastMember>) {
    if (cast.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.shelf_cast), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(cast.take(MAX_CAST), key = { it.id }) { member -> CastCard(member) }
        }
    }
}

@Composable
private fun CastCard(member: CastMember) {
    Column(Modifier.width(88.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            TmdbImage.profile(member.profilePath)?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(member.name.orEmpty(), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        member.character?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val MAX_CAST = 20
