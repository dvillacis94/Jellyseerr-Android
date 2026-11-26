package com.nesoi.seerr.android.auth

import com.nesoi.seerr.android.core.PublicSettings

/** Which sign-in methods a server offers, taken from its public settings. */
data class LoginOptions(
    val plex: Boolean,
    /** Username and password against the paired Jellyfin or Emby server. */
    val mediaServer: MediaServer?,
    /** Email and password for a Seerr account. */
    val local: Boolean,
) {
    enum class MediaServer(val displayName: String) {
        Jellyfin("Jellyfin"),
        Emby("Emby"),
    }

    val hasPasswordForms: Boolean get() = mediaServer != null || local
    val isEmpty: Boolean get() = !plex && !hasPasswordForms

    companion object {
        fun from(settings: PublicSettings): LoginOptions {
            val mediaServerLogin = settings.mediaServerLogin ?: true
            return LoginOptions(
                plex = settings.mediaServerType == PLEX && mediaServerLogin,
                mediaServer = when {
                    !mediaServerLogin -> null
                    settings.mediaServerType == JELLYFIN -> MediaServer.Jellyfin
                    settings.mediaServerType == EMBY -> MediaServer.Emby
                    else -> null
                },
                local = settings.localLogin ?: false,
            )
        }

        private const val PLEX = 1
        private const val JELLYFIN = 2
        private const val EMBY = 3
    }
}
