package com.nesoi.seerr.android.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Turns what a user types into candidate Seerr base URLs. */
object ServerAddress {
    /**
     * Candidate base URLs to probe, in order.
     *
     * - `requests.example.com` gives `https://requests.example.com`, then `http://…`
     * - `http://192.168.1.10:5055/` gives `http://192.168.1.10:5055/`
     * - `https://host/seerr/api/v1` gives `https://host/seerr/`
     *
     * @throws ServerConnectionException with [ServerConnectionError.EmptyAddress] or
     * [ServerConnectionError.InvalidAddress].
     */
    fun candidates(input: String): List<HttpUrl> {
        var text = input.trim()
        if (text.isEmpty()) throw ServerConnectionException(ServerConnectionError.EmptyAddress)

        val schemes = text.indexOf("://").let { index ->
            if (index < 0) {
                listOf("https", "http")
            } else {
                val scheme = text.substring(0, index).lowercase()
                if (scheme != "http" && scheme != "https") {
                    throw ServerConnectionException(ServerConnectionError.InvalidAddress)
                }
                text = text.substring(index + 3)
                listOf(scheme)
            }
        }

        return schemes.map { scheme ->
            val url = "$scheme://$text".toHttpUrlOrNull()
                ?: throw ServerConnectionException(ServerConnectionError.InvalidAddress)
            normalized(url)
        }
    }

    /**
     * Whether [input] points at something on the local network: a private or loopback IP,
     * `localhost`, or a name such as `seerr.local` or `nas`. Android 17 needs a permission
     * before an app may reach those.
     */
    fun looksLocal(input: String): Boolean {
        val host = try {
            candidates(input).first().host
        } catch (_: ServerConnectionException) {
            return false
        }
        val octets = host.split(".").mapNotNull { it.toIntOrNull() }
        if (octets.size == 4 && host.all { it.isDigit() || it == '.' }) {
            val (a, b) = octets
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (':' in host) {
            return host == "::1" || host.startsWith("fe80") || host.startsWith("fc") || host.startsWith("fd")
        }
        return !host.contains('.') ||
            host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa") || host.endsWith(".internal")
    }

    /** Whether everything sent to [url], sign-in included, travels unencrypted. */
    fun isUnencrypted(url: HttpUrl): Boolean = !url.isHttps

    private fun normalized(url: HttpUrl): HttpUrl {
        val segments = url.pathSegments.filter { it.isNotEmpty() }.toMutableList()
        if (segments.size >= 2 &&
            segments[segments.size - 2].equals("api", ignoreCase = true) &&
            segments.last().equals("v1", ignoreCase = true)
        ) {
            repeat(2) { segments.removeAt(segments.lastIndex) }
        }
        val builder = url.newBuilder().query(null).fragment(null).encodedPath("/")
        segments.forEach { builder.addEncodedPathSegment(it) }
        builder.addPathSegment("")
        return builder.build()
    }
}
