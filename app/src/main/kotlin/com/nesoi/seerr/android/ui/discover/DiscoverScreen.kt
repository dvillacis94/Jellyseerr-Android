package com.nesoi.seerr.android.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.MediaItem
import com.nesoi.seerr.android.media.MediaList
import com.nesoi.seerr.android.media.SignedInModel
import com.nesoi.seerr.android.ui.media.ErrorMessage
import com.nesoi.seerr.android.ui.media.MediaShelf
import com.nesoi.seerr.android.ui.media.SearchResults
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(model: SignedInModel, onSeeAll: (MediaList) -> Unit, onOpen: (MediaItem) -> Unit, modifier: Modifier = Modifier) {
    val discover = model.discover
    val search = model.search
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    val allFailed by discover.allFailed.collectAsStateWithLifecycle(initialValue = false)
    val firstError by discover.firstError.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    LaunchedEffect(discover) { discover.load() }

    Column(modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.tab_discover),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        SearchField(query, search::setQuery, onSearch = { search.submit(); focus.clearFocus() })

        val loader = results
        when {
            loader != null -> SearchResults(loader, search.trimmedQuery, onOpen)
            allFailed -> ErrorMessage(firstError, onRetry = discover::retry)
            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        try { discover.refresh() } finally { refreshing = false }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    discover.lists.forEach { list ->
                        MediaShelf(
                            title = stringResource(list.title),
                            loader = discover.shelves.getValue(list),
                            onOpen = onOpen,
                            onSeeAll = { onSeeAll(list) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, onSearch: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        shape = CircleShape,
        placeholder = { Text(stringResource(R.string.discover_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.discover_search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
