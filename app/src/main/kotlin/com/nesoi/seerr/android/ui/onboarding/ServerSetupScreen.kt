package com.nesoi.seerr.android.ui.onboarding

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nesoi.seerr.android.core.ServerAddress
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.tooling.preview.Preview
import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.auth.ServerSetupModel
import com.nesoi.seerr.android.auth.ServerSetupState
import com.nesoi.seerr.android.core.ServerConnectionError
import com.nesoi.seerr.android.ui.theme.SeerrTheme

@Composable
fun ServerSetupScreen(model: ServerSetupModel) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    var canReachLocalNetwork by remember { mutableStateOf(LocalNetwork.isGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canReachLocalNetwork = granted
        model.connect()
    }

    // Android 17 blocks servers on the home network until the person allows it. Ask up front
    // when the address is clearly local, and offer it again if a hostname turns out to be.
    ServerSetupContent(
        state = state,
        onAddressChange = model::setAddress,
        onConnect = {
            if (!canReachLocalNetwork && ServerAddress.looksLocal(state.address)) {
                permissionLauncher.launch(LocalNetwork.PERMISSION)
            } else {
                model.connect()
            }
        },
        showLocalNetworkHelp = !canReachLocalNetwork && state.error == ServerConnectionError.Unreachable,
        onAllowLocalNetwork = { permissionLauncher.launch(LocalNetwork.PERMISSION) },
    )
}

/** The permission Android 17 asks for before an app may reach devices on the local network. */
private object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun ServerSetupContent(
    state: ServerSetupState,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    showLocalNetworkHelp: Boolean = false,
    onAllowLocalNetwork: () -> Unit = {},
) {
    OnboardingLayout(stringResource(R.string.setup_title), stringResource(R.string.setup_subtitle)) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.setup_address_label)) },
                placeholder = { Text(stringResource(R.string.setup_address_hint)) },
                singleLine = true,
                isError = state.error != null,
                supportingText = {
                    Text(state.error?.let { stringResource(it.message) } ?: stringResource(R.string.setup_address_help))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onConnect() }),
            )
            if (showLocalNetworkHelp) {
                TextButton(onClick = onAllowLocalNetwork) { Text(stringResource(R.string.setup_allow_local_network)) }
            }
            Button(onClick = onConnect, enabled = state.canConnect, modifier = Modifier.fillMaxWidth()) {
                if (state.isConnecting) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(stringResource(R.string.setup_continue))
                }
            }
        }
    }
}

private val ServerConnectionError.message: Int
    get() = when (this) {
        ServerConnectionError.EmptyAddress -> R.string.error_empty_address
        ServerConnectionError.InvalidAddress -> R.string.error_invalid_address
        ServerConnectionError.Unreachable -> R.string.error_unreachable
        ServerConnectionError.UntrustedCertificate -> R.string.error_untrusted_certificate
        ServerConnectionError.NotSeerr -> R.string.error_not_seerr
        ServerConnectionError.NotInitialized -> R.string.error_not_initialized
    }

@Preview
@Composable
private fun ServerSetupPreview() {
    SeerrTheme { ServerSetupContent(ServerSetupState(address = "requests.example.com"), {}, {}) }
}
