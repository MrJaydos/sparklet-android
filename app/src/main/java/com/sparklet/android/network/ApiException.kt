package com.sparklet.android.network

// The `message` strings here are diagnostic — they're for logs, stack traces
// and crash reports, not for people. Don't render them in UI: anything that
// shows an error to a user should map the exception to its own copy, with
// wording that makes sense for what the user was actually trying to do (a
// 401 means "your session ended" in the feed but "that sign-in link expired"
// during sign-in). See AuthSession.signInErrorMessage.
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object Unauthorized : ApiException("unauthorized")
    data class Server(val status: Int, val responseBody: String) : ApiException("server error $status")
    data class Decoding(val error: Throwable) : ApiException("decoding failed", error)
    data class Transport(val error: Throwable) : ApiException("transport failed", error)
}
