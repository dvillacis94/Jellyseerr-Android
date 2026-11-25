package com.nesoi.seerr.android.core.net

/** A response Seerr answered with an error, or one the app couldn't make sense of. */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** 401. Carries the server's message when it sent one (such as `INVALID_CREDENTIALS`). */
    class Unauthorized(val serverMessage: String?) : ApiException(serverMessage ?: "Unauthorized")

    /** 403. Carries the server's message when it sent one (such as "Access denied."). */
    class Forbidden(val serverMessage: String?) : ApiException(serverMessage ?: "Forbidden")

    object NotFound : ApiException("Not found") {
        private fun readResolve(): Any = NotFound
    }

    class Conflict(val serverMessage: String?) : ApiException(serverMessage ?: "Conflict")

    /** 400, 422 and 5xx, with the server's message when it sent one. */
    class Server(val serverMessage: String) : ApiException(serverMessage)

    class Http(val code: Int) : ApiException("HTTP error $code")

    class Decoding(cause: Throwable) : ApiException("Unexpected response from the server", cause)
}
