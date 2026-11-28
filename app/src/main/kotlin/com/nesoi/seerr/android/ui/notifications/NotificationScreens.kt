package com.nesoi.seerr.android.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.notifications.NotificationChannel
import com.nesoi.seerr.android.notifications.NotificationSettingsModel
import com.nesoi.seerr.android.notifications.NotificationSettingsModel.Summary
import com.nesoi.seerr.android.notifications.Problem
import com.nesoi.seerr.android.notifications.SaveResult
import com.nesoi.seerr.android.notifications.UserNotificationSettings
import com.nesoi.seerr.android.ui.media.ErrorMessage
import kotlinx.coroutines.launch

/** Profile › Notifications: the channels the server offers, each with a page of its own. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(model: NotificationSettingsModel, onBack: () -> Unit, onOpen: (NotificationChannel) -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle()
    val error by model.loadError.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(model) { if (model.settings.value == null) model.load() }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.notifications_title)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val loaded = settings
            when {
                loaded != null -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.notifications_services), Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    model.channels(loaded).forEach { channel ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(channel) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(channel.title), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Text(summaryText(model.summary(loaded, channel)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.notifications_footer), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                error != null -> ErrorMessage(error, onRetry = { scope.launch { model.load() } })
                else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun summaryText(summary: Summary): String = when (summary) {
    Summary.Off -> stringResource(R.string.notifications_off)
    Summary.All -> stringResource(R.string.notifications_all)
    is Summary.Some -> stringResource(R.string.notifications_some, summary.on, summary.of)
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.list_back)) }
}

/** One channel's account details and the events it sends, saved together. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationChannelScreen(model: NotificationSettingsModel, channel: NotificationChannel, onBack: () -> Unit) {
    val loaded by model.settings.collectAsStateWithLifecycle()
    val settings = loaded
    if (settings == null) {
        // Only reachable if the list was left before it loaded.
        LaunchedEffect(model) { onBack() }
        return
    }
    var draft by remember { mutableStateOf(settings) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<Problem?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val kinds = model.kinds(draft, channel)
    val changed = draft.normalized() != settings.normalized()
    LaunchedEffect(draft) { problem = null; failure = null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(channel.title)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(Modifier.padding(end = 16.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(
                            enabled = changed,
                            onClick = {
                                scope.launch {
                                    saving = true
                                    when (val result = model.save(draft, channel)) {
                                        SaveResult.Saved -> model.settings.value?.let { draft = it }
                                        is SaveResult.Invalid -> problem = result.problem
                                        is SaveResult.Failed -> failure = result.message ?: ""
                                    }
                                    saving = false
                                }
                            },
                        ) { Text(stringResource(R.string.notifications_save)) }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // At the top, where it is seen without scrolling past the switches.
            problem?.let { Text(stringResource(it.message), color = MaterialTheme.colorScheme.error) }
            failure?.let { Text(it.ifEmpty { stringResource(R.string.error_generic) }, color = MaterialTheme.colorScheme.error) }

            AccountFields(channel, draft, { draft = it }, enabled = !saving)

            if (channel == NotificationChannel.Discord && draft.enabledTypes(channel) == 0) {
                Text(stringResource(R.string.notifications_discord_off), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (kinds.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.notifications_notify_about), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                kinds.forEach { kind ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(stringResource(kind.title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(kind.description(model.isIssueManager)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = kind.isOn(draft.types(channel)), onCheckedChange = { draft = draft.with(channel, kind, it) }, enabled = !saving)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountFields(channel: NotificationChannel, draft: UserNotificationSettings, onChange: (UserNotificationSettings) -> Unit, enabled: Boolean) {
    when (channel) {
        NotificationChannel.Email -> Unit
        NotificationChannel.Discord -> {
            val ids = draft.discordIds.orEmpty().ifEmpty { listOf("") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.notifications_discord_ids), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                ids.forEachIndexed { index, id ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(stringResource(R.string.notifications_discord_id), id, { value -> onChange(draft.copy(discordIds = ids.toMutableList().also { it[index] = value })) }, KeyboardType.Number, enabled = enabled, modifier = Modifier.weight(1f))
                        if (ids.size > 1) {
                            IconButton(onClick = { onChange(draft.copy(discordIds = ids.filterIndexed { i, _ -> i != index })) }, enabled = enabled) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.notifications_remove))
                            }
                        }
                    }
                }
                TextButton(onClick = { onChange(draft.copy(discordIds = ids + "")) }, enabled = enabled) {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.notifications_add_id))
                }
                Hint(stringResource(R.string.notifications_discord_hint))
            }
        }
        NotificationChannel.Telegram -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.notifications_chat_id), draft.telegramChatId.orEmpty(), { onChange(draft.copy(telegramChatId = it)) }, KeyboardType.Text, enabled = enabled)
            Field(stringResource(R.string.notifications_thread_id), draft.telegramMessageThreadId.orEmpty(), { onChange(draft.copy(telegramMessageThreadId = it)) }, KeyboardType.Number, enabled = enabled)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.notifications_silent), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = draft.telegramSendSilently == true, onCheckedChange = { onChange(draft.copy(telegramSendSilently = it)) }, enabled = enabled)
            }
            draft.telegramBotUsername?.let { Hint(stringResource(R.string.notifications_telegram_hint, it)) }
        }
        NotificationChannel.Pushbullet -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.notifications_access_token), draft.pushbulletAccessToken.orEmpty(), { onChange(draft.copy(pushbulletAccessToken = it)) }, KeyboardType.Password, secret = true, enabled = enabled)
            Hint(stringResource(R.string.notifications_pushbullet_hint))
        }
        NotificationChannel.Pushover -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(stringResource(R.string.notifications_app_token), draft.pushoverApplicationToken.orEmpty(), { onChange(draft.copy(pushoverApplicationToken = it)) }, KeyboardType.Password, secret = true, enabled = enabled)
            Field(stringResource(R.string.notifications_user_key), draft.pushoverUserKey.orEmpty(), { onChange(draft.copy(pushoverUserKey = it)) }, KeyboardType.Password, secret = true, enabled = enabled)
            Hint(stringResource(R.string.notifications_pushover_hint))
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, keyboard: KeyboardType, enabled: Boolean, modifier: Modifier = Modifier, secret: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
