package com.nesoi.seerr.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(primary = AccentLight, onPrimary = Color.White)
private val DarkColors = darkColorScheme(primary = AccentDark, onPrimary = SeerrNight)

/** Seerr's colours on Material 3. The accent stays the brand's, not the wallpaper's. */
@Composable
fun SeerrTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
