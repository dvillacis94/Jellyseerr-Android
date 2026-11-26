package com.nesoi.seerr.android.core

import okhttp3.HttpUrl

/** A Seerr server that answered `/status` and `/settings/public`. */
data class ServerInfo(val url: HttpUrl, val status: ServerStatus, val settings: PublicSettings)

enum class ServerConnectionError {
    EmptyAddress,
    InvalidAddress,
    Unreachable,
    UntrustedCertificate,
    NotSeerr,
    NotInitialized,
}

class ServerConnectionException(val error: ServerConnectionError) : Exception(error.name)
