package com.nesoi.seerr.android.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.nesoi.seerr.android.media.TitleType
import com.nesoi.seerr.android.media.TmdbImage
import com.nesoi.seerr.android.media.seriesStatuses
import com.nesoi.seerr.android.media.sortOptions
import java.util.Calendar

private const val OLDEST_YEAR = 1900
private val runtimes = (15..240 step 15).toList()

private sealed interface Page {
    data object Main : Page
    data object Genres : Page
    data object Statuses : Page
    data object Language : Page
    data object Services : Page
}

/** One row of a pick list: a code, what to call it and, for streaming services, a logo. */
private data class Choice(val id: Int, val name: String, val logo: String? = null)

/**
 * Pick filters for a tab. Like the iOS app, the short choices are popups on this page and the long
 * lists (genres, languages, streaming services) open as pages of their own with a search box.
 * Changes are held until Apply, so backing out leaves the grid alone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersSheet(model: BrowseModel, onDismiss: () -> Unit) {
    val applied by model.filters.collectAsStateWithLifecycle()
    val genres by model.options.genres.collectAsStateWithLifecycle()
    val languages by model.options.languages.collectAsStateWithLifecycle()
    val regions by model.options.regions.collectAsStateWithLifecycle()
    val providers by model.options.providers.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(applied) }
    var page by remember { mutableStateOf<Page>(Page.Main) }
    var naming by remember { mutableStateOf(false) }
    var pickingRegion by remember { mutableStateOf(false) }
    val isMovies = model.type == TitleType.Movie
    val region = draft.region ?: model.defaultRegion() ?: "US"

    LaunchedEffect(Unit) { model.options.load() }
    LaunchedEffect(region) { model.options.loadProviders(region) }
    BackHandler(enabled = page != Page.Main) { page = Page.Main }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Box(Modifier.fillMaxSize().navigationBarsPadding()) {
            when (page) {
                Page.Main -> MainPage(
                    model = model,
                    draft = draft,
                    onDraft = { draft = it },
                    genreNames = genres.orEmpty().associate { it.id to it.name },
                    languageName = languages?.firstOrNull { it.code == draft.language }?.name ?: draft.language,
                    onOpen = { page = it },
                    onCancel = onDismiss,
                    onApply = { model.apply(draft); onDismiss() },
                    onSave = { naming = true },
                )
                Page.Genres -> MultiSelectPage(
                    title = stringResource(R.string.filters_genres),
                    footer = stringResource(R.string.filters_genres_footer),
                    choices = genres?.map { Choice(it.id, it.name) },
                    selected = draft.genres,
                    onChange = { draft = draft.copy(genres = it) },
                    onBack = { page = Page.Main },
                )
                Page.Statuses -> MultiSelectPage(
                    title = stringResource(R.string.filters_status),
                    footer = stringResource(R.string.filters_status_footer),
                    choices = seriesStatuses.map { (code, label) -> Choice(code, stringResource(label)) },
                    selected = draft.statuses,
                    onChange = { draft = draft.copy(statuses = it) },
                    onBack = { page = Page.Main },
                )
                Page.Language -> LanguagePage(
                    languages = languages?.map { it.code to it.name },
                    selected = draft.language,
                    onPick = { draft = draft.copy(language = it); page = Page.Main },
                    onBack = { page = Page.Main },
                )
                Page.Services -> MultiSelectPage(
                    title = stringResource(R.string.filters_services),
                    footer = stringResource(R.string.filters_services_footer),
                    choices = providers[region]?.map { Choice(it.id, it.name, it.logoPath) },
                    selected = draft.providers,
                    onChange = { draft = draft.copy(providers = it, region = region) },
                    onBack = { page = Page.Main },
                    header = {
                        ValueRow(
                            stringResource(R.string.filters_region_label),
                            regions?.firstOrNull { it.code == region }?.name ?: region,
                            onClick = { if (regions != null) pickingRegion = true },
                        )
                        HorizontalDivider()
                    },
                )
            }
        }
    }

    if (naming) {
        NameDialog(
            title = stringResource(R.string.filters_save_title),
            initial = "",
            onDismiss = { naming = false },
            onConfirm = { name -> model.save(name, draft); onDismiss() },
        )
    }
    if (pickingRegion) {
        SearchPickerDialog(
            title = stringResource(R.string.filters_region_label),
            options = regions.orEmpty().map { it.code to it.name },
            selected = region,
            // Another region has other services, so the ones chosen no longer apply.
            onPick = { code -> if (code != null && code != region) draft = draft.copy(region = code, providers = emptySet()) },
            onDismiss = { pickingRegion = false },
            allowAny = false,
        )
    }
}

@Composable
private fun MainPage(
    model: BrowseModel,
    draft: DiscoverFilters,
    onDraft: (DiscoverFilters) -> Unit,
    genreNames: Map<Int, String>,
    languageName: String?,
    onOpen: (Page) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onSave: () -> Unit,
) {
    val isMovies = model.type == TitleType.Movie
    val any = stringResource(R.string.filters_any)
    val newest = remember { Calendar.getInstance().get(Calendar.YEAR) + 1 }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.filters_cancel)) }
            Text(
                stringResource(R.string.filters_title),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            TextButton(onClick = onApply) { Text(stringResource(R.string.filters_apply)) }
        }
        HorizontalDivider()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val sorts = model.type.sortOptions()
            DropdownRow(
                stringResource(R.string.filters_sort),
                sorts.firstOrNull { it.value == draft.sort }?.let { stringResource(it.label) } ?: draft.sort,
                sorts.map { it.value to stringResource(it.label) },
            ) { onDraft(draft.copy(sort = it)) }

            SectionGap()
            ValueRow(stringResource(R.string.filters_genres), summary(draft.genres, genreNames, any), onClick = { onOpen(Page.Genres) }, chevron = true)
            if (!isMovies) {
                val statusNames = seriesStatuses.associate { (code, label) -> code to stringResource(label) }
                ValueRow(stringResource(R.string.filters_status), summary(draft.statuses, statusNames, any), onClick = { onOpen(Page.Statuses) }, chevron = true)
            }
            ValueRow(stringResource(R.string.filters_language), languageName ?: any, onClick = { onOpen(Page.Language) }, chevron = true)

            SectionGap()
            val years: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to any) + (newest downTo OLDEST_YEAR).map { it to "$it" }
            SectionTitle(stringResource(if (isMovies) R.string.filters_released else R.string.filters_first_aired))
            DropdownRow(stringResource(R.string.filters_from), draft.yearFrom?.toString() ?: any, years) { onDraft(draft.copy(yearFrom = it)) }
            DropdownRow(stringResource(R.string.filters_to), draft.yearTo?.toString() ?: any, years) { onDraft(draft.copy(yearTo = it)) }

            SectionGap()
            val scores: List<Pair<Int, String>> = listOf(0 to any) + (1..9).map { it to stringResource(R.string.filters_score_value, it) }
            DropdownRow(stringResource(R.string.filters_score), scores.first { it.first == draft.minScore }.second, scores) { onDraft(draft.copy(minScore = it)) }
            val times: List<Pair<Int?, String>> = listOf<Pair<Int?, String>>(null to any) + runtimes.map { it to stringResource(R.string.filters_minutes, it) }
            DropdownRow(stringResource(R.string.filters_runtime_min), draft.runtimeMin?.let { stringResource(R.string.filters_minutes, it) } ?: any, times) { onDraft(draft.copy(runtimeMin = it)) }
            DropdownRow(stringResource(R.string.filters_runtime_max), draft.runtimeMax?.let { stringResource(R.string.filters_minutes, it) } ?: any, times) { onDraft(draft.copy(runtimeMax = it)) }

            SectionGap()
            ValueRow(
                stringResource(R.string.filters_services),
                if (draft.providers.isEmpty()) any else stringResource(R.string.filters_selected, draft.providers.size),
                onClick = { onOpen(Page.Services) },
                chevron = true,
            )

            SectionGap()
            TextButton(onClick = onSave, enabled = !draft.isEmpty, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.filters_save)) }
            TextButton(onClick = { onDraft(DiscoverFilters()) }, enabled = !draft.isEmpty, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.filters_reset))
            }

            SectionGap()
        }
    }
}

/** "Any", the name when one or two are picked, else how many. */
@Composable
private fun summary(ids: Set<Int>, names: Map<Int, String>, any: String): String {
    if (ids.isEmpty()) return any
    val known = ids.mapNotNull { names[it] }.sorted()
    return if (known.size == ids.size && known.size <= 2) known.joinToString(", ") else stringResource(R.string.filters_selected, ids.size)
}

@Composable
private fun SectionGap() = Box(Modifier.padding(top = 20.dp))

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

/** A label with its current value on the right. Tapping it opens something. */
@Composable
internal fun ValueRow(title: String, value: String, onClick: () -> Unit, chevron: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Box(Modifier.weight(1f))
        Text(
            value,
            Modifier.padding(start = 12.dp).widthIn(max = 200.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The same width with or without an arrow, so the values line up.
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else Box(Modifier.size(24.dp))
    }
}

/** A row that drops a short list of choices down from its value. */
@Composable
internal fun <T> DropdownRow(title: String, value: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ValueRow(title, value, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { (code, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onPick(code); open = false })
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back)) }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.filters_search)) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** A long list to tick items from, with its own search box. */
@Composable
private fun MultiSelectPage(
    title: String,
    footer: String,
    choices: List<Choice>?,
    selected: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    onBack: () -> Unit,
    header: @Composable () -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(choices, query) { choices.orEmpty().filter { it.name.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(title, onBack)
        header()
        SearchBox(query) { query = it }
        when {
            choices == null -> Text(stringResource(R.string.filters_loading), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            choices.isEmpty() -> Text(stringResource(R.string.filters_no_services), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { choice ->
                    val on = choice.id in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange(if (on) selected - choice.id else selected + choice.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (choice.logo != null) {
                            AsyncImage(TmdbImage.logo(choice.logo), contentDescription = null, modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)))
                        }
                        Text(choice.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        if (on) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                item { Text(footer, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/** Pick one language, or Any. */
@Composable
private fun LanguagePage(languages: List<Pair<String, String>>?, selected: String?, onPick: (String?) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(languages, query) { languages.orEmpty().filter { it.second.contains(query.trim(), ignoreCase = true) } }
    Column(Modifier.fillMaxSize()) {
        PageHeader(stringResource(R.string.filters_language), onBack)
        SearchBox(query) { query = it }
        if (languages == null) {
            Text(stringResource(R.string.filters_loading), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.weight(1f)) {
                if (query.isBlank()) item { PickRow(stringResource(R.string.filters_any), selected == null) { onPick(null) } }
                items(shown, key = { it.first }) { (code, name) -> PickRow(name, code == selected) { onPick(code) } }
            }
        }
    }
}

@Composable
private fun PickRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** A searchable popup of (code, name), for the streaming region. [onPick] gets null for "Any". */
@Composable
internal fun SearchPickerDialog(
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
                    if (allowAny && query.isBlank()) item { PickRow(stringResource(R.string.filters_any), selected == null) { onPick(null); onDismiss() } }
                    items(shown, key = { it.first }) { (code, name) -> PickRow(name, code == selected) { onPick(code); onDismiss() } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.filters_cancel)) } },
    )
}

@Composable
internal fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
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
