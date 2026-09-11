package com.sparklet.android.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.FriendsResponse
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.FriendsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FriendsViewModel(private val authSession: AuthSession) : ViewModel() {
    private val api = FriendsApi()

    private val _response = MutableStateFlow<FriendsResponse?>(null)
    val response: StateFlow<FriendsResponse?> = _response.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // The route's own message ("request sent", "already friends", "no user
    // with that code") is worth surfacing verbatim — it's written for a
    // person, unlike the diagnostic strings on ApiException.
    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun loadIfNeeded() {
        if (_response.value == null) load()
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _response.value = api.fetch(authSession.token.value)
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                // Best-effort.
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun sendRequest(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                _statusMessage.value = api.sendRequest(trimmed, authSession.token.value).message
                // A sent request lands in `outgoing`, so refresh rather than
                // guessing at the new shape locally.
                load()
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                _statusMessage.value = "Couldn't send that request."
            }
        }
    }

    fun accept(friendshipId: String) = mutate { api.accept(friendshipId, authSession.token.value) }

    fun remove(friendshipId: String) = mutate { api.remove(friendshipId, authSession.token.value) }

    fun clearStatus() {
        _statusMessage.value = null
    }

    // Accept/decline/cancel/unfriend all move a row between (or out of) the
    // three lists, so each re-reads the authoritative shape instead of
    // patching it locally.
    private fun mutate(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                load()
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                _statusMessage.value = "That didn't go through."
            }
        }
    }
}
