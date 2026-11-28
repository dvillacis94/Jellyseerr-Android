package com.nesoi.seerr.android.ui.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.issues.formatDateTime
import com.nesoi.seerr.android.ui.profile.Avatar
import com.nesoi.seerr.android.ui.profile.avatarUrl
import com.nesoi.seerr.android.users.PermissionOption
import com.nesoi.seerr.android.users.QuotaOverride
import com.nesoi.seerr.android.users.UserEditModel
import com.nesoi.seerr.android.users.UserManagementRules
import kotlinx.coroutines.launch
import okhttp3.HttpUrl

/** A user's permissions and request limits, for someone with Manage Users. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserEditScreen(model: UserEditModel, serverUrl: HttpUrl, onBack: () -> Unit, onSaved: (User) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val saved = stringResource(R.string.user_edit_saved)
    LaunchedEffect(model) { model.load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(state.user.displayNameOrEmail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back)) } },
                actions = {
                    if (model.canEditPermissions || model.canEditLimits) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                        } else {
                            TextButton(
                                enabled = model.hasChanges(state),
                                onClick = {
                                    scope.launch {
                                        model.save()?.let {
                                            onSaved(it)
                                            snackbar.showSnackbar(saved)
                                        }
                                    }
                                },
                            ) { Text(stringResource(R.string.user_edit_save)) }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header(state.user, serverUrl)
            state.error?.let {
                Text(it.ifEmpty { stringResource(R.string.users_delete_failed) }, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (model.canEditLimits) {
                val general = state.general
                when {
                    general != null -> {
                        QuotaSection(R.string.user_edit_movie_requests, state.movieQuota, general.global("globalMovieQuotaLimit"), general.global("globalMovieQuotaDays"), null, model::setMovieQuota)
                        QuotaSection(R.string.user_edit_series_requests, state.tvQuota, general.global("globalTvQuotaLimit"), general.global("globalTvQuotaDays"), R.string.user_edit_series_footer, model::setTvQuota)
                    }
                    state.loadError != null -> Section(stringResource(R.string.user_edit_limits_title)) {
                        TextButton(onClick = { scope.launch { model.load() } }) { Text(stringResource(R.string.user_edit_limits_failed)) }
                    }
                    else -> Section(stringResource(R.string.user_edit_limits_title)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.user_edit_limits_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Section(
                stringResource(R.string.user_edit_permissions),
                footer = stringResource(
                    when {
                        state.user.id == UserManagementRules.OWNER_ID -> R.string.user_edit_footer_owner
                        model.rules.isYou(state.user) -> R.string.user_edit_footer_self
                        !model.rules.isOwner -> R.string.user_edit_footer_admin
                        else -> R.string.user_edit_footer_default
                    },
                ),
            ) {
                model.options.forEach { option ->
                    PermissionRow(model, option, null)
                    option.children.forEach { child -> PermissionRow(model, child, option, Modifier.padding(start = 20.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Header(user: User, serverUrl: HttpUrl) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(user.avatarUrl(serverUrl))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.displayNameOrEmail, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                user.email?.takeIf { it != user.displayNameOrEmail }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(details(user, withCount = false), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        user.requestCount?.let { count -> LabeledValue(stringResource(R.string.user_edit_requests), count.toString()) }
        formatDateTime(user.createdAt)?.let { joined -> LabeledValue(stringResource(R.string.user_edit_joined), joined) }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Section(title: String, footer: String? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
        footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun PermissionRow(model: UserEditModel, option: PermissionOption, parent: PermissionOption?, modifier: Modifier = Modifier) {
    val state by model.state.collectAsStateWithLifecycle()
    val shown = model.optionState(option, parent, state)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(option.title, model.mediaServerName), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(option.description, model.mediaServerName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = shown.isOn, onCheckedChange = { model.toggle(option) }, enabled = shown.isEnabled)
    }
}

@Composable
private fun QuotaSection(
    title: Int,
    quota: QuotaOverride,
    defaultLimit: Int?,
    defaultDays: Int?,
    footer: Int?,
    onChange: (QuotaOverride) -> Unit,
) {
    Section(stringResource(title), footer = footer?.let { stringResource(it) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.user_edit_override), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = quota.isOverridden, onCheckedChange = { onChange(quota.copy(isOverridden = it)) })
        }
        if (quota.isOverridden) {
            StepperRow(
                stringResource(R.string.user_edit_quota_requests),
                if (quota.limit == 0) stringResource(R.string.user_edit_unlimited) else quota.limit.toString(),
                canDecrease = quota.limit > 0, canIncrease = quota.limit < MAX_QUOTA,
                onDecrease = { onChange(quota.copy(limit = quota.limit - 1)) },
                onIncrease = { onChange(quota.copy(limit = quota.limit + 1)) },
            )
            if (quota.limit > 0) {
                StepperRow(
                    stringResource(R.string.user_edit_quota_every),
                    if (quota.days == 1) stringResource(R.string.user_edit_day) else stringResource(R.string.user_edit_days, quota.days),
                    canDecrease = quota.days > 1, canIncrease = quota.days < MAX_QUOTA,
                    onDecrease = { onChange(quota.copy(days = quota.days - 1)) },
                    onIncrease = { onChange(quota.copy(days = quota.days + 1)) },
                )
            }
        } else {
            LabeledValue(stringResource(R.string.user_edit_server_default), describe(defaultLimit, defaultDays))
        }
    }
}

private const val MAX_QUOTA = 100

@Composable
private fun StepperRow(label: String, value: String, canDecrease: Boolean, canIncrease: Boolean, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        IconButton(onClick = onDecrease, enabled = canDecrease) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.user_edit_decrease)) }
        Box(Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.Center) { Text(value, style = MaterialTheme.typography.bodyLarge) }
        IconButton(onClick = onIncrease, enabled = canIncrease) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.user_edit_increase)) }
    }
}

/** "Unlimited", "5 requests every 7 days", "1 request per day". */
@Composable
private fun describe(limit: Int?, days: Int?): String {
    if (limit == null || limit <= 0) return stringResource(R.string.user_edit_unlimited)
    val requests = if (limit == 1) stringResource(R.string.user_edit_quota_one) else stringResource(R.string.user_edit_quota_many, limit)
    return when {
        days == null -> requests
        days == 1 -> stringResource(R.string.user_edit_quota_per_day, requests)
        else -> stringResource(R.string.user_edit_quota_every_days, requests, days)
    }
}

private fun kotlinx.serialization.json.JsonObject.global(name: String): Int? =
    (this[name] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
