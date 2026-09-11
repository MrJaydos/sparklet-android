package com.sparklet.android.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.auth.LoginController
import com.sparklet.android.model.ProfileDetailsResponse
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.ProfileApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val authSession: AuthSession) : ViewModel() {
    private val api = ProfileApi()
    private val loginController = LoginController()

    private val _details = MutableStateFlow<ProfileDetailsResponse?>(null)
    val details: StateFlow<ProfileDetailsResponse?> = _details.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadIfNeeded() {
        if (_details.value == null) load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _details.value = api.fetchDetails(authSession.token.value)
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                // Best-effort.
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                api.updateName(trimmed, authSession.token.value)
                _details.value = _details.value?.copy(name = trimmed)
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                // Leave the old name showing rather than a value the server
                // didn't accept.
            }
        }
    }

    // The real, user-initiated sign-out: revoke the Session row server-side
    // first, then clear locally. AuthSession.signOut alone is the 401 path —
    // it only drops the local copy, which would leave a live token behind.
    fun signOut() {
        val token = authSession.token.value
        viewModelScope.launch {
            if (token != null) loginController.signOut(token)
            authSession.signOut()
        }
    }
}
