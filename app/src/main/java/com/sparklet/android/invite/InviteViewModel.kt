package com.sparklet.android.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.InviteResponse
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.InviteApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class InviteViewModel(private val authSession: AuthSession) : ViewModel() {
    private val api = InviteApi()

    private val _response = MutableStateFlow<InviteResponse?>(null)
    val response: StateFlow<InviteResponse?> = _response.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    fun accept(refId: String) {
        if (_isLoading.value || _response.value != null) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _response.value = api.accept(refId, authSession.token.value)
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                _failed.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }
}
