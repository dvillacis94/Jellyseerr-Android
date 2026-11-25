package com.nesoi.seerr.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        enableEdgeToEdge()
        val container = (application as SeerrApplication).container
        setContent {
            SeerrTheme {
                AppRoot(viewModel(factory = viewModelFactory { initializer { AppViewModel(container) } }))
            }
        }
    }
}
