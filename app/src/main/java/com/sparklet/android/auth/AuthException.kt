package com.sparklet.android.auth

sealed class AuthException(message: String) : Exception(message) {
    // Unlike ASWebAuthenticationSession on iOS, Custom Tabs has no
    // cancellation callback — this is inferred from the user resuming the
    // app without a redirect ever having arrived. See AuthRedirect.kt.
    data object Cancelled : AuthException("sign-in was cancelled")
}
