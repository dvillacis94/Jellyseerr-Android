package com.nesoi.seerr.android.ui.onboarding

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.auth.LoginModel
import com.nesoi.seerr.android.auth.PasswordMode
import com.nesoi.seerr.android.auth.SignInFailure
import com.nesoi.seerr.android.core.ServerAddress

@Composable
fun LoginScreen(model: LoginModel, onChangeServer: () -> Unit) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(model) {
        model.browserUrls.collect { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(it)) }
    }

    val options = model.options
    val title = model.settings.applicationTitle ?: "Seerr"
    OnboardingLayout(title, model.serverUrl.host) {
        if (ServerAddress.isUnencrypted(model.serverUrl)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.login_not_secure)) })
                Text(stringResource(R.string.login_not_secure_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (options.plex) {
                if (state.isWaitingForPlex) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.login_plex_waiting), Modifier.weight(1f))
                        TextButton(onClick = model::cancelPlex) { Text(stringResource(R.string.login_plex_cancel)) }
                    }
                } else {
                    Button(onClick = model::signInWithPlex, enabled = !state.isSigningIn, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.login_plex))
                    }
                }
                if (options.hasPasswordForms) OrDivider()
            }

            if (options.hasPasswordForms) {
                if (options.mediaServer != null && options.local) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.passwordMode == PasswordMode.MediaServer,
                            onClick = { model.setPasswordMode(PasswordMode.MediaServer) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2),
                        ) { Text(stringResource(R.string.login_mode_media_server, options.mediaServer.displayName)) }
                        SegmentedButton(
                            selected = state.passwordMode == PasswordMode.Local,
                            onClick = { model.setPasswordMode(PasswordMode.Local) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2),
                        ) { Text(stringResource(R.string.login_mode_local)) }
                    }
                }
                if (state.passwordMode == PasswordMode.MediaServer) {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = model::setUsername,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.login_username)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    )
                } else {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = model::setEmail,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.login_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    )
                }
                OutlinedTextField(
                    value = state.password,
                    onValueChange = model::setPassword,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.login_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { model.signInWithPassword() }),
                )
                OutlinedButton(onClick = model::signInWithPassword, enabled = state.canSubmitPassword(), modifier = Modifier.fillMaxWidth()) {
                    if (state.isSigningIn && !state.isWaitingForPlex) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.login_sign_in))
                    }
                }
            }

            if (options.isEmpty) {
                Text(stringResource(R.string.login_no_methods), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            state.failure?.let { failure ->
                Text(failureText(failure), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }

        TextButton(onClick = onChangeServer) { Text(stringResource(R.string.login_change_server)) }
    }
}

@Composable
private fun OrDivider() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text(stringResource(R.string.login_or), color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun failureText(failure: SignInFailure): String = when (failure) {
    SignInFailure.InvalidCredentials -> stringResource(R.string.sign_in_invalid_credentials)
    SignInFailure.AccessDenied -> stringResource(R.string.sign_in_access_denied)
    SignInFailure.MethodDisabled -> stringResource(R.string.sign_in_method_disabled)
    SignInFailure.Unreachable -> stringResource(R.string.sign_in_unreachable)
    SignInFailure.PlexTimedOut -> stringResource(R.string.sign_in_plex_timed_out)
    is SignInFailure.Message -> failure.text
}
