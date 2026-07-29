package com.sparklet.android.network

sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data object Unauthorized : ApiException("unauthorized")
    data class Server(val status: Int, val responseBody: String) : ApiException("server error $status")
    data class Decoding(val error: Throwable) : ApiException("decoding failed", error)
    data class Transport(val error: Throwable) : ApiException("transport failed", error)
}
