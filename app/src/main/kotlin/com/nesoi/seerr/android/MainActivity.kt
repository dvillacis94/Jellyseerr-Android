package com.nesoi.seerr.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nesoi.seerr.android.ui.AppRoot
import com.nesoi.seerr.android.ui.AppViewModel
import com.nesoi.seerr.android.ui.theme.SeerrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val container = (application as SeerrApplication).container
        setContent {
            val viewModel: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(container) } })
            val appearance by viewModel.appearance.collectAsStateWithLifecycle()
            val dark = appearance.isDark(isSystemInDarkTheme())
            // The bars' icons follow the app's own choice, not the phone's.
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            SeerrTheme(darkTheme = dark) {
                AppRoot(viewModel)
            }
        }
    }
}
