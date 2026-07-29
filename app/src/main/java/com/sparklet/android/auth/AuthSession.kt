package com.sparklet.android.auth

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// App-wide auth state. `token` is the bearer credential sent as
// `Authorization: Bearer <token>` on every API request (see
// network/ApiClient.kt) — the native equivalent of the session cookie the
// web client relies on. Owned by SparkletApplication so it outlives any
// single Activity instance.
class AuthSession(context: Context) {
    private val tokenStore = TokenStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    init {
        scope.launch { _token.value = tokenStore.load() }
    }

    fun signIn(token: String) {
        _token.value = token
        scope.launch { tokenStore.save(token) }
    }

    // Local-only: clears the token here without revoking it server-side.
    // Used today only as the automatic response to a 401 (see
    // FeedViewModel/StatsHeaderViewModel), where the token is already
    // invalid — nothing to revoke. An explicit user-initiated sign-out
    // should call LoginController.signOut(token) first, which actually
    // revokes the Session row, then this.
    fun signOut() {
        _token.value = null
        scope.launch { tokenStore.clear() }
    }
}
