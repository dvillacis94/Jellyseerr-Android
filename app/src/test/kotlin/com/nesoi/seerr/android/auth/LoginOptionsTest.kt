package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.core.PublicSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginOptionsTest {
    private fun options(type: Int?, mediaServerLogin: Boolean? = null, local: Boolean? = null) =
        LoginOptions.from(PublicSettings(true, mediaServerType = type, mediaServerLogin = mediaServerLogin, localLogin = local))

    @Test fun `plex servers offer plex`() {
        val options = options(1)
        assertTrue(options.plex)
        assertNull(options.mediaServer)
        assertFalse(options.local)
    }

    @Test fun `jellyfin and emby servers offer a password form`() {
        assertEquals(LoginOptions.MediaServer.Jellyfin, options(2).mediaServer)
        assertEquals(LoginOptions.MediaServer.Emby, options(3).mediaServer)
        assertFalse(options(2).plex)
    }

    @Test fun `media server sign in can be turned off`() {
        assertTrue(options(1, mediaServerLogin = false, local = true).let { !it.plex && it.local })
        assertNull(options(2, mediaServerLogin = false).mediaServer)
    }

    @Test fun `local sign in is off unless the server says so`() {
        assertFalse(options(1).local)
        assertTrue(options(1, local = true).local)
    }

    @Test fun `an unconfigured server offers nothing`() {
        assertTrue(options(4).isEmpty)
        assertTrue(options(null).isEmpty)
        assertFalse(options(4, local = true).isEmpty)
    }
}
