package com.sparklet.android.auth

import android.content.Context
import android.net.Uri
import com.sparklet.android.network.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// App-wide auth state, and the owner of the whole sign-in flow. `token` is
// the bearer credential sent as `Authorization: Bearer <token>` on every API
// request (see network/ApiClient.kt) — the native equivalent of the session
// cookie the web client relies on.
//
// Sign-in lives here, at process scope, rather than in LoginScreen, because
// the Custom Tabs flow necessarily leaves the app: the one-time code comes
// back as a fresh Intent, and by then the Activity and its composition may
// both be gone. An earlier version bridged the redirect into a suspend
// function running in LoginScreen's rememberCoroutineScope via a
// replay-less SharedFlow, which lost the code outright in two real cases:
//
//   - Activity recreation mid-sign-in (a configuration change — on a
//     foldable, simply folding or unfolding while the Custom Tab is open).
//     The composition is torn down, its coroutine scope is cancelled, and
//     the emission that follows reaches zero subscribers and is dropped.
//   - Process death while backgrounded in the browser. The redirect Intent
//     restarts the app, but no sign-in coroutine is waiting any more.
//
// Both burn a single-use, 60-second code and silently drop the user back on
// the login screen. Handling the redirect here removes the dependency on
// anything surviving: onAuthRedirect exchanges whatever code arrives,
// whether or not this process is the one that started the flow, and the
// exchange runs in this class's own scope, which outlives every Activity.
class AuthSession(
    context: Context,
    private val loginController: LoginController = LoginController(),
) {
    private val tokenStore = TokenStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    sealed interface SignInState {
        data object Idle : SignInState
        data object InProgress : SignInState
        data class Failed(val message: String) : SignInState
    }

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    private val _signInState = MutableStateFlow<SignInState>(SignInState.Idle)
    val signInState: StateFlow<SignInState> = _signInState.asStateFlow()

    // Whether a redirect is still expected. Only used to infer cancellation
    // (see onActivityResumed) — never as a precondition for accepting a code,
    // since after process death no flow was ever "started" in this process.
    @Volatile
    private var awaitingRedirect = false

    init {
        scope.launch { _token.value = tokenStore.load() }
    }

    // Step 1-2 of the mobile-auth contract: open /login?mobileScheme=... in an
    // external user-agent. Custom Tabs needs an Activity Context, which this
    // class deliberately has no dependency on, so launching is injected.
    fun beginSignIn(launchCustomTab: (Uri) -> Unit) {
        if (_signInState.value == SignInState.InProgress) return
        _signInState.value = SignInState.InProgress
        awaitingRedirect = true
        launchCustomTab(loginController.loginUrl())
    }

    // Step 3: the sparklet-android://auth?code=... redirect arrived. Exchange
    // it for a real token over a direct HTTPS POST from the app itself —
    // never through the browser. Deliberately does not check
    // awaitingRedirect: a code that arrives after process death is still
    // valid and still ours to redeem.
    fun onAuthRedirect(uri: Uri) {
        awaitingRedirect = false
        val code = uri.getQueryParameter("code")
        if (code == null) {
            _signInState.value = SignInState.Failed("Sign-in was cancelled.")
            return
        }
        _signInState.value = SignInState.InProgress
        scope.launch {
            try {
                val token = loginController.exchangeCode(code)
                _token.value = token
                tokenStore.save(token)
                _signInState.value = SignInState.Idle
            } catch (e: Exception) {
                _signInState.value = SignInState.Failed(signInErrorMessage(e))
            }
        }
    }

    // Custom Tabs has no cancellation callback (unlike iOS's
    // ASWebAuthenticationSession), so backing out of the tab has no signal of
    // its own. It's inferred from the Activity resuming while a redirect is
    // still expected: when the OS actually delivers the redirect, the Intent
    // is handled first (onCreate/onNewIntent both run before onResume), which
    // clears awaitingRedirect — so reaching here with it still set means no
    // redirect came.
    fun onActivityResumed() {
        if (awaitingRedirect) {
            awaitingRedirect = false
            _signInState.value = SignInState.Failed("Sign-in was cancelled.")
        }
    }

    // ApiException's own messages are diagnostic ("unauthorized", "server
    // error 503") and were previously shown to the user verbatim. Each case
    // is worded for what the person was actually doing — signing in — and for
    // what they can do next.
    private fun signInErrorMessage(e: Throwable): String = when (e) {
        // The one-time code is single-use and lives 60 seconds, so a 401 here
        // means it expired or was already redeemed — not that the account is
        // wrong. Retrying genuinely works, so say so.
        is ApiException.Unauthorized -> "That sign-in link expired. Tap Sign in to try again."
        is ApiException.Transport -> "Couldn't reach Sparklet. Check your connection and try again."
        is ApiException.Server -> "Sparklet is having trouble right now. Try again in a moment."
        else -> "Something went wrong signing in. Please try again."
    }

    fun clearSignInError() {
        if (_signInState.value is SignInState.Failed) _signInState.value = SignInState.Idle
    }

    // Local-only: clears the token here without revoking it server-side.
    // Used today only as the automatic response to a 401 (see
    // FeedViewModel/StatsHeaderViewModel), where the token is already
    // invalid — nothing to revoke. An explicit user-initiated sign-out
    // should call LoginController.signOut(token) first, which actually
    // revokes the Session row, then this.
    fun signOut() {
        _token.value = null
        _signInState.value = SignInState.Idle
        scope.launch { tokenStore.clear() }
    }
}
