package com.nesoi.seerr.android.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil3.compose.AsyncImage
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.media.BrowseModel
import com.nesoi.seerr.android.media.DiscoverFilters
import com.nesoi.seerr.android.media.SavedFilter
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.media.TmdbImage
import com.nesoi.seerr.android.media.seriesStatuses
import com.nesoi.seerr.android.media.sortOptions
import java.util.Calendar

private const val TOP_SERVICES = 24
private const val RUNTIME_STEP = 15
private const val RUNTIME_TOP = 300
private const val OLDEST_YEAR = 1900

/**
 * Pick filters for a tab. Changes are held here until Done, so backing out leaves the grid alone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(model: BrowseModel, onDismiss: () -> Unit) {
    val applied by model.filters.collectAsStateWithLifecycle()
    val saved by model.saved.collectAsStateWithLifecycle()
    val genres by model.options.genres.collectAsStateWithLifecycle()
    val languages by model.options.languages.collectAsStateWithLifecycle()
    val regions by model.options.regions.collectAsStateWithLifecycle()
    val providers by model.options.providers.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(applied) }
    var naming by remember { mutableStateOf<Naming?>(null) }
    var pickingLanguage by remember { mutableStateOf(false) }
    var pickingRegion by remember { mutableStateOf(false) }
    var showAllServices by remember { mutableStateOf(false) }
    val isMovies = model.type == TitleType.Movie
    val region = draft.region ?: model.defaultRegion() ?: "US"

    LaunchedEffect(Unit) { model.options.load() }
    LaunchedEffect(region) { model.options.loadProviders(region) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.filters_title), style = MaterialTheme.typography.titleLarge)

            if (saved.isNotEmpty()) {
                Section(stringResource(R.string.filters_saved)) {
                    saved.forEach { filter ->
                        SavedRow(
                            filter,
                            onUse = { draft = filter.filters },
                            onRename = { naming = Naming.Rename(filter) },
                            onDelete = { model.delete(filter.id) },
                        )
                    }
                }
            }

            Section(stringResource(R.string.filters_sort)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.type.sortOptions().forEach { option ->
                        FilterChip(
                            selected = draft.sort == option.value,
                            onClick = { draft = draft.copy(sort = option.value) },
                            label = { Text(stringResource(option.label)) },
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
            }

            Section(stringResource(R.string.filters_genres)) {
                if (genres == null) {
                    Text(stringResource(R.string.filters_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        genres.orEmpty().forEach { genre ->
                            FilterChip(
                                selected = genre.id in draft.genres,
                                onClick = { draft = draft.copy(genres = draft.genres.toggle(genre.id)) },
                                label = { Text(genre.name) },
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            Section(stringResource(if (isMovies) R.string.filters_released else R.string.filters_first_aired)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    YearPicker(stringResource(R.string.filters_from), draft.yearFrom, { draft = draft.copy(yearFrom = it) }, Modifier.weight(1f))
                    YearPicker(stringResource(R.string.filters_to), draft.yearTo, { draft = draft.copy(yearTo = it) }, Modifier.weight(1f))
                }
            }

            Section(stringResource(R.string.filters_language)) {
                OutlinedButton(onClick = { pickingLanguage = true }, enabled = languages != null) {
                    Text(
                        languages?.firstOrNull { it.code == draft.language }?.name
                            ?: draft.language
                            ?: stringResource(R.string.filters_any),
                    )
                }
            }

            Section(stringResource(R.string.filters_score), value = draft.minScore.takeIf { it > 0 }?.let { stringResource(R.string.filters_score_value, it) }) {
                Slider(
                    value = draft.minScore.toFloat(),
                    onValueChange = { draft = draft.copy(minScore = it.toInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                )
            }

            Section(stringResource(R.string.filters_runtime), value = runtimeLabel(draft)) {
                RangeSlider(
                    value = (draft.runtimeMin ?: 0).toFloat()..(draft.runtimeMax ?: RUNTIME_TOP).toFloat(),
                    onValueChange = { range ->
                        draft = draft.copy(
                            runtimeMin = range.start.toInt().takeIf { it > 0 },
                            runtimeMax = range.endInclusive.toInt().takeIf { it < RUNTIME_TOP },
                        )
                    },
                    valueRange = 0f..RUNTIME_TOP.toFloat(),
                    steps = RUNTIME_TOP / RUNTIME_STEP - 1,
                )
            }

            Section(stringResource(R.string.filters_services)) {
                OutlinedButton(onClick = { pickingRegion = true }, enabled = regions != null) {
                    Text(stringResource(R.string.filters_region, regions?.firstOrNull { it.code == region }?.name ?: region))
                }
                val list = providers[region]
                when {
                    list == null -> Text(stringResource(R.string.filters_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    list.isEmpty() -> Text(stringResource(R.string.filters_no_services), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Servers list hundreds, so start with the most used and anything chosen.
                        val shown = if (showAllServices) list else list.filterIndexed { index, it -> index < TOP_SERVICES || it.id in draft.providers }
                        shown.forEach { provider ->
                            FilterChip(
                                selected = provider.id in draft.providers,
                                onClick = { draft = draft.copy(providers = draft.providers.toggle(provider.id), region = region) },
                                label = { Text(provider.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = provider.logoPath?.let { logo ->
                                    { AsyncImage(TmdbImage.logo(logo), contentDescription = null, modifier = Modifier.size(20.dp)) }
                                },
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        if (!showAllServices && list.size > shown.size) {
                            TextButton(onClick = { showAllServices = true }) { Text(stringResource(R.string.filters_show_all, list.size)) }
                        }
                    }
                }
            }

            if (!isMovies) {
                Section(stringResource(R.string.filters_status)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        seriesStatuses.forEach { (code, label) ->
                            FilterChip(
                                selected = code in draft.statuses,
                                onClick = { draft = draft.copy(statuses = draft.statuses.toggle(code)) },
                                label = { Text(stringResource(label)) },
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { draft = DiscoverFilters() }, enabled = !draft.isEmpty) { Text(stringResource(R.string.filters_reset)) }
                TextButton(onClick = { naming = Naming.Save }, enabled = !draft.isEmpty) { Text(stringResource(R.string.filters_save)) }
                Box(Modifier.weight(1f))
                Button(onClick = { model.apply(draft); onDismiss() }) { Text(stringResource(R.string.filters_done)) }
            }
        }
    }

    naming?.let { mode ->
        NameDialog(
            title = stringResource(if (mode is Naming.Rename) R.string.filters_rename_title else R.string.filters_save_title),
            initial = (mode as? Naming.Rename)?.filter?.name.orEmpty(),
            onDismiss = { naming = null },
            onConfirm = { name ->
                when (mode) {
                    Naming.Save -> { model.save(name, draft); onDismiss() }
                    is Naming.Rename -> model.rename(mode.filter.id, name)
                }
                naming = null
            },
        )
    }
    if (pickingLanguage) {
        PickerDialog(
            title = stringResource(R.string.filters_language),
            options = languages.orEmpty().map { it.code to it.name },
            selected = draft.language,
            onPick = { draft = draft.copy(language = it) },
            onDismiss = { pickingLanguage = false },
        )
    }
    if (pickingRegion) {
        PickerDialog(
            title = stringResource(R.string.filters_services),
            options = regions.orEmpty().map { it.code to it.name },
            selected = region,
            // Another region has other services, so the ones chosen no longer apply.
            onPick = { code -> if (code != null && code != region) draft = draft.copy(region = code, providers = emptySet()) },
            onDismiss = { pickingRegion = false },
            allowAny = false,
        )
    }
}

private sealed interface Naming {
    data object Save : Naming
    data class Rename(val filter: SavedFilter) : Naming
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

@Composable
private fun runtimeLabel(filters: DiscoverFilters): String? = when {
    filters.runtimeMin != null && filters.runtimeMax != null -> stringResource(R.string.filters_runtime_between, filters.runtimeMin, filters.runtimeMax)
    filters.runtimeMin != null -> stringResource(R.string.filters_runtime_at_least, filters.runtimeMin)
    filters.runtimeMax != null -> stringResource(R.string.filters_runtime_at_most, filters.runtimeMax)
    else -> null
}

@Composable
private fun Section(title: String, value: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            value?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
        }
        content()
    }
}

@Composable
private fun SavedRow(filter: SavedFilter, onUse: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onUse), verticalAlignment = Alignment.CenterVertically) {
        Text(filter.name, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        IconButton(onClick = onRename) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.filters_rename)) }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.filters_delete)) }
    }
}

@Composable
private fun YearPicker(label: String, value: Int?, onChange: (Int?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val newest = remember { Calendar.getInstance().get(Calendar.YEAR) + 1 }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${value ?: stringResource(R.string.filters_any)}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            DropdownMenuItem(text = { Text(stringResource(R.string.filters_any)) }, onClick = { onChange(null); open = false })
            for (year in newest downTo OLDEST_YEAR) {
                DropdownMenuItem(text = { Text("$year") }, onClick = { onChange(year); open = false })
            }
        }
    }
}

/** A searchable list of (code, name). [onPick] gets null for "Any". */
@Composable
private fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
    allowAny: Boolean = true,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(options, query) { options.filter { it.second.contains(query.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.filters_search)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (allowAny && query.isBlank()) {
                        item { PickerRow(stringResource(R.string.filters_any), selected == null) { onPick(null); onDismiss() } }
                    }
                    items(shown, key = { it.first }) { (code, name) ->
                        PickerRow(name, code == selected) { onPick(code); onDismiss() }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.filters_cancel)) } },
    )
}

@Composable
private fun PickerRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.filters_name)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.filters_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.filters_cancel)) } },
    )
}
