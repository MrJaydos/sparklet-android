package com.sparklet.android.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.NotificationsResponse
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.NotificationsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(private val authSession: AuthSession) : ViewModel() {
    private val api = NotificationsApi()

    private val _response = MutableStateFlow<NotificationsResponse?>(null)
    val response: StateFlow<NotificationsResponse?> = _response.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadIfNeeded() {
        if (_response.value == null) load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _response.value = api.fetch(authSession.token.value)
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                // Best-effort, same as the rest of the read-only screens.
            } finally {
                _isLoading.value = false
            }
        }
    }

    // Marks everything read server-side, then reflects it locally rather than
    // re-fetching. Only `notifications` and `unreadCount` are affected —
    // admin/friend alerts are live-computed action items that clear when the
    // underlying thing is resolved, so marking read must not touch them.
    fun markAllRead() {
        val current = _response.value ?: return
        if (current.unreadCount == 0) return
        viewModelScope.launch {
            try {
                api.markAllRead(authSession.token.value)
                _response.value = current.copy(
                    unreadCount = 0,
                    notifications = current.notifications.map { it.copy(read = true) },
                )
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                // Leave the unread state alone if the call failed — showing
                // them as read when the server disagrees is worse than not.
            }
        }
    }
}
