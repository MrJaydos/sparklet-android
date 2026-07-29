package com.sparklet.android.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

// Bridges MainActivity's onNewIntent (fired when the OS delivers the
// sparklet-android://auth?code=... redirect) to LoginController's suspend
// signIn(), which is waiting on a completely separate call stack. This
// indirection exists because Custom Tabs — unlike iOS's
// ASWebAuthenticationSession — has no direct callback: the redirect arrives
// as a new Intent, decoupled from whatever launched the tab.
//
// Cancellation (the user just closes the tab without completing sign-in) has
// no signal of its own either. It's inferred from MainActivity.onResume()
// firing while a redirect was still awaited: onNewIntent is guaranteed to
// run before onResume when the OS actually delivers the redirect intent
// (singleTask activities receive onNewIntent, then resume), so if
// awaitingRedirect is still true by the time onResume runs, no redirect came
// and the user backed out of the tab.
object AuthRedirect {
    sealed interface Event {
        data class Code(val code: String) : Event
        data object Cancelled : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    @Volatile
    private var awaitingRedirect = false

    fun beginAwaiting() {
        awaitingRedirect = true
    }

    fun onRedirect(uri: Uri) {
        awaitingRedirect = false
        val code = uri.getQueryParameter("code")
        _events.tryEmit(if (code != null) Event.Code(code) else Event.Cancelled)
    }

    fun onActivityResumed() {
        if (awaitingRedirect) {
            awaitingRedirect = false
            _events.tryEmit(Event.Cancelled)
        }
    }
}
