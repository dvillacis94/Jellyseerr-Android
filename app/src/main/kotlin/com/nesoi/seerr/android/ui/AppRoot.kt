package com.nesoi.seerr.android.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.ui.onboarding.LoginScreen
import com.nesoi.seerr.android.ui.onboarding.ServerSetupScreen
import com.nesoi.seerr.android.ui.shell.MainScaffold

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val current = state) {
        AppState.Loading -> CenteredColumn { CircularProgressIndicator() }
        is AppState.ServerSetup -> ServerSetupScreen(current.model)
        is AppState.Login -> LoginScreen(current.model, onChangeServer = viewModel::changeServer)
        is AppState.Unreachable -> CenteredColumn {
            Text(stringResource(R.string.unreachable_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                current.host?.let { stringResource(R.string.unreachable_message, it) } ?: stringResource(R.string.unreachable_message_unknown),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = viewModel::start) { Text(stringResource(R.string.unreachable_retry)) }
            OutlinedButton(onClick = viewModel::changeServer) { Text(stringResource(R.string.login_change_server)) }
        }
        is AppState.SignedIn -> {
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            MainScaffold(
                current.user,
                current.serverUrl,
                current.model,
                appearance,
                onAppearance = viewModel::setAppearance,
                onSignOut = viewModel::signOut,
                onChangeServer = viewModel::changeServer,
            )
        }
    }
}

@Composable
private fun CenteredColumn(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}
