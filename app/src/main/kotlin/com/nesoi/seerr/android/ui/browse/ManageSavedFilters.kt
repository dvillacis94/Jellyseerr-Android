package com.nesoi.seerr.android.ui.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.BrowseModel
import com.nesoi.seerr.android.media.SavedFilter

/** Rename or delete the tab's saved filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSavedFilters(model: BrowseModel, onDismiss: () -> Unit) {
    val saved by model.saved.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<SavedFilter?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.filters_manage_title), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            if (saved.isEmpty()) {
                Text(stringResource(R.string.filters_manage_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn {
                items(saved, key = { it.id }) { filter ->
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(filter.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { renaming = filter }) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.filters_rename)) }
                        IconButton(onClick = { model.delete(filter.id) }) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.filters_delete)) }
                    }
                }
            }
        }
    }
    renaming?.let { filter ->
        NameDialog(
            title = stringResource(R.string.filters_rename_title),
            initial = filter.name,
            onDismiss = { renaming = null },
            onConfirm = { model.rename(filter.id, it); renaming = null },
        )
    }
}
