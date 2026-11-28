package com.nesoi.seerr.android.ui.users

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.ui.media.ErrorMessage
import com.nesoi.seerr.android.ui.media.SearchField
import com.nesoi.seerr.android.ui.profile.Avatar
import com.nesoi.seerr.android.ui.profile.accountType
import com.nesoi.seerr.android.ui.profile.avatarUrl
import com.nesoi.seerr.android.users.UsersModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

private const val TYPING_PAUSE_MS = 350L

/** Profile › Users, for people with Manage Users. [onOpen] goes to a user's page. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun UsersScreen(model: UsersModel, serverUrl: HttpUrl, onBack: () -> Unit, onOpen: (User) -> Unit) {
    val list by model.list.collectAsStateWithLifecycle()
    val state by list.state.collectAsStateWithLifecycle()
    val total by model.total.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var text by remember { mutableStateOf(query) }
    var refreshing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<User?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    // Wait for typing to pause before asking the server.
    LaunchedEffect(text) {
        if (text.trim() != query) delay(TYPING_PAUSE_MS)
        model.search(text)
    }
    LaunchedEffect(list) { list.loadIfNeeded() }
    LaunchedEffect(list, listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { list.loadMoreIfNeeded(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.users_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(text, stringResource(R.string.users_search_hint), onChange = { text = it }, onSearch = { model.search(text) })
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
                            stringResource(R.string.users_empty, query),
                            Modifier.align(Alignment.Center).padding(32.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(state.items, key = { it.id }) { user ->
                            UserRow(
                                user, isYou = model.rules.isYou(user), serverUrl = serverUrl,
                                onClick = { onOpen(user) },
                                onDelete = if (model.rules.canDelete(user)) ({ deleting = user }) else null,
                            )
                        }
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (state.isLoading) {
                                    CircularProgressIndicator()
                                } else {
                                    Text(
                                        pluralStringResource(R.plurals.users_count, total, total),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { user ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.users_delete_title, user.displayNameOrEmail)) },
            text = { Text(stringResource(R.string.users_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { deleteError = model.delete(user) }
                }) { Text(stringResource(R.string.users_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.filters_cancel)) } },
        )
    }
    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text(stringResource(R.string.users_delete_failed)) },
            text = { if (message.isNotEmpty()) Text(message) },
            confirmButton = { TextButton(onClick = { deleteError = null }) { Text(stringResource(R.string.users_ok)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserRow(user: User, isYou: Boolean, serverUrl: HttpUrl, onClick: () -> Unit, onDelete: (() -> Unit)?) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = if (onDelete != null) ({ menu = true }) else null)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(user.avatarUrl(serverUrl), size = 40.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(user.displayNameOrEmail, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isYou) Text(stringResource(R.string.users_you), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(details(user), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.users_delete), color = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; onDelete?.invoke() },
            )
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/** "Admin · Plex · 12 requests". */
@Composable
internal fun details(user: User, withCount: Boolean = true): String = listOfNotNull(
    stringResource(com.nesoi.seerr.android.users.UserManagementRules.role(user)),
    user.accountType?.let { stringResource(it) },
    user.requestCount?.takeIf { withCount }?.let { pluralStringResource(R.plurals.users_requests_count, it, it) },
).joinToString(" · ")
