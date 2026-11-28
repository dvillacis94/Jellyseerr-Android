package com.nesoi.seerr.android.ui

import com.nesoi.seerr.android.R
import com.nesoi.seerr.android.core.User
import com.nesoi.seerr.android.ui.profile.accountType
import com.nesoi.seerr.android.ui.profile.avatarUrl
import com.nesoi.seerr.android.ui.theme.Appearance
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTest {
    private val server = "https://seerr.example.com/".toHttpUrl()

    @Test fun `appearance falls back to the system setting`() {
        assertEquals(Appearance.System, Appearance.from(null))
        assertEquals(Appearance.System, Appearance.from("sepia"))
        assertEquals(Appearance.Dark, Appearance.from("Dark"))
    }

    @Test fun `light and dark ignore the phone and system follows it`() {
        assertTrue(Appearance.Dark.isDark(systemDark = false))
        assertFalse(Appearance.Light.isDark(systemDark = true))
        assertTrue(Appearance.System.isDark(systemDark = true))
        assertFalse(Appearance.System.isDark(systemDark = false))
    }

    @Test fun `an avatar path is read against the server and a whole address is kept`() {
        assertEquals("https://seerr.example.com/avatarproxy/abc", User(id = 1, avatar = "/avatarproxy/abc").avatarUrl(server))
        assertEquals("https://plex.tv/users/x/avatar", User(id = 1, avatar = "https://plex.tv/users/x/avatar").avatarUrl(server))
        assertNull(User(id = 1, avatar = "").avatarUrl(server))
        assertNull(User(id = 1).avatarUrl(server))
    }

    @Test fun `the account type names the sign-in`() {
        assertEquals(R.string.account_plex, User(id = 1, userType = 1).accountType)
        assertEquals(R.string.account_seerr, User(id = 1, userType = 2).accountType)
        assertEquals(R.string.account_jellyfin, User(id = 1, userType = 3).accountType)
        assertEquals(R.string.account_emby, User(id = 1, userType = 4).accountType)
        assertNull(User(id = 1).accountType)
    }
}
