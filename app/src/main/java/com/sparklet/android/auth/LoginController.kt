package com.sparklet.android.auth

import android.net.Uri
import androidx.core.net.toUri
import com.sparklet.android.config.AppConfig
import com.sparklet.android.network.ApiClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

// Mirrors the backend's mobile auth contract exactly (sparklet repo:
// src/lib/mobile-auth.ts, src/app/api/auth/mobile-{complete,exchange,session}
// — pushed to main as commit 89be8be, verified end-to-end there as of
// 2026-07-28, see AGENTS.md). This is an RFC 8252-style code exchange, not a
// token handed back directly through the deep link: a token embedded in a
// redirect URL sits in browser history/OS logs, and an unvalidated scheme
// could hand it to whatever app the OS resolves that scheme to. The
// one-time code is the only thing that crosses the browser/app boundary;
// the real Bearer token is fetched over a direct HTTPS POST from the app
// itself.
//
// `launchCustomTab` is injected rather than called directly here because
// launching a Custom Tab needs an Android Context/Activity, which this
// class deliberately has no dependency on.
class LoginController(private val apiClient: ApiClient = ApiClient) {

    @Serializable
    private data class ExchangeRequest(val code: String)

    @Serializable
    private data class ExchangeResponse(val token: String, val expires: String)

    // Step 1-2: drive /login?mobileScheme=..., then wait for the one-time
    // code from the backend's sparklet-android://auth?code=<code> redirect
    // (delivered via AuthRedirect, not a direct callback — see its comment).
    suspend fun signIn(launchCustomTab: (Uri) -> Unit): String {
        val loginUrl = AppConfig.apiBaseUrl.newBuilder()
            .addPathSegment("login")
            .addQueryParameter("mobileScheme", AppConfig.AUTH_CALLBACK_SCHEME)
            .build()

        AuthRedirect.beginAwaiting()
        launchCustomTab(loginUrl.toString().toUri())

        val code = when (val event = AuthRedirect.events.first()) {
            is AuthRedirect.Event.Code -> event.code
            AuthRedirect.Event.Cancelled -> throw AuthException.Cancelled
        }
        return exchangeCode(code)
    }

    // Step 3: exchange the one-time code for a Bearer token over a direct
    // HTTPS request — never through the browser/Custom Tab, so the real
    // credential never travels through a URL. A 60-second-lived, single-use
    // code; an expired or replayed one 401s.
    private suspend fun exchangeCode(code: String): String {
        val response = apiClient.post<ExchangeRequest, ExchangeResponse>(
            path = "api/auth/mobile-exchange",
            body = ExchangeRequest(code),
            token = null,
        )
        return response.token
    }

    // Step 4: DELETE /api/auth/mobile-session revokes this token
    // specifically — the browser's own cookie session (if any) is untouched.
    suspend fun signOut(token: String) {
        runCatching { apiClient.delete("api/auth/mobile-session", token) }
    }
}
