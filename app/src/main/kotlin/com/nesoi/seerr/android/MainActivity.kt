package com.nesoi.seerr.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.nesoi.seerr.android.ui.WelcomeScreen
import com.nesoi.seerr.android.ui.theme.SeerrTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SeerrTheme {
                WelcomeScreen()
            }
        }
    }
}
