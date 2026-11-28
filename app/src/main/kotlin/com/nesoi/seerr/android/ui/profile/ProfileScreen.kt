package com.nesoi.seerr.android.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.ui.theme.Appearance
import okhttp3.HttpUrl

/** Who is signed in, how the app looks, which server it talks to, and how to leave. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User,
    serverUrl: HttpUrl,
    serverName: String?,
    appearance: Appearance,
    onAppearance: (Appearance) -> Unit,
    onSignOut: () -> Unit,
    onChangeServer: () -> Unit,
    onOpenNotifications: () -> Unit,
    /** Null when this person can't see issues. */
    onOpenIssues: (() -> Unit)?,
    /** Null when this person can't manage users. */
    onOpenUsers: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var confirming by remember { mutableStateOf<Leave?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineMedium)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Avatar(user.avatarUrl(serverUrl))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.displayNameOrEmail, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                user.email?.takeIf { it != user.displayNameOrEmail }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                user.accountType?.let {
                    Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        NavRow(Icons.Filled.Notifications, stringResource(R.string.profile_notifications), onOpenNotifications)

        if (onOpenIssues != null) {
            NavRow(Icons.Filled.ReportProblem, stringResource(R.string.profile_issues), onOpenIssues)
        }

        if (onOpenUsers != null) {
            NavRow(Icons.Filled.Group, stringResource(R.string.profile_users), onOpenUsers)
        }

        Section(stringResource(R.string.profile_appearance), footer = stringResource(R.string.profile_appearance_footer)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Appearance.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == appearance,
                        onClick = { onAppearance(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, Appearance.entries.size),
                    ) { Text(stringResource(option.label)) }
                }
            }
        }

        Section(stringResource(R.string.profile_server)) {
            LabeledValue(stringResource(R.string.profile_server_name), serverName ?: stringResource(R.string.profile_server_default_name))
            LabeledValue(stringResource(R.string.profile_server_address), serverUrl.host)
            OutlinedButton(onClick = { confirming = Leave.ChangeServer }) { Text(stringResource(R.string.login_change_server)) }
        }

        OutlinedButton(onClick = { confirming = Leave.SignOut }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.home_sign_out), color = MaterialTheme.colorScheme.error)
        }
    }

    confirming?.let { leave ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text(stringResource(if (leave == Leave.SignOut) R.string.profile_sign_out_title else R.string.profile_change_server_title)) },
            text = { if (leave == Leave.ChangeServer) Text(stringResource(R.string.profile_change_server_message)) },
            confirmButton = {
                TextButton(onClick = { confirming = null; if (leave == Leave.SignOut) onSignOut() else onChangeServer() }) {
                    Text(
                        stringResource(if (leave == Leave.SignOut) R.string.home_sign_out else R.string.profile_change_server_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text(stringResource(R.string.filters_cancel)) } },
        )
    }
}

@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class Leave { SignOut, ChangeServer }

/** The avatar, or a person-shaped placeholder when there is none or it hasn't loaded. */
@Composable
internal fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (url != null) AsyncImage(url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
private fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Seerr's avatars are either whole addresses (Plex, Gravatar) or paths on the server (`/avatarproxy/...`). */
internal fun User.avatarUrl(server: HttpUrl): String? = avatar?.takeIf { it.isNotBlank() }?.let { server.resolve(it)?.toString() }

/** What kind of account this is, for the line under the name. */
internal val User.accountType: Int?
    get() = when (userType) {
        1 -> R.string.account_plex
        2 -> R.string.account_seerr
        3 -> R.string.account_jellyfin
        4 -> R.string.account_emby
        else -> null
    }

