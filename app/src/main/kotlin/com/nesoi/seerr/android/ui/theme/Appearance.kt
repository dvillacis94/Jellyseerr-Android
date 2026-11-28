package com.nesoi.seerr.android.ui.theme

import com.nesoi.seerr.android.R

/** Whether the app follows the phone's light or dark setting, or always uses one. */
enum class Appearance(val label: Int) {
    System(R.string.appearance_system),
    Light(R.string.appearance_light),
    Dark(R.string.appearance_dark);

    /** Whether to draw dark, given what the phone is set to. */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        System -> systemDark
        Light -> false
        Dark -> true
    }

    companion object {
        const val KEY = "appearance"
        fun from(stored: String?): Appearance = entries.firstOrNull { it.name == stored } ?: System
    }
}
