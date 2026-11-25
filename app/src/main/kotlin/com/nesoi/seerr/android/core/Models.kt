package com.nesoi.seerr.android.core

import kotlinx.serialization.Serializable

/** `GET /status` (public). */
@Serializable
data class ServerStatus(val version: String)

/** `GET /settings/public` (public). Drives which sign-in methods are offered. */
@Serializable
data class PublicSettings(
    val initialized: Boolean,
    val applicationTitle: String? = null,
    val localLogin: Boolean? = null,
    val mediaServerLogin: Boolean? = null,
    /** 1 Plex, 2 Jellyfin, 3 Emby, 4 not configured. */
    val mediaServerType: Int? = null,
    val plexClientIdentifier: String? = null,
)

@Serializable
data class User(
    val id: Int,
    /** Seerr strips `email` from users the caller isn't allowed to see, so it's optional. */
    val email: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val avatar: String? = null,
    val permissions: Int = 0,
    val plexUsername: String? = null,
    val jellyfinUsername: String? = null,
) {
    val displayNameOrEmail: String
        get() = displayName ?: username ?: plexUsername ?: jellyfinUsername ?: email ?: "User $id"
}
