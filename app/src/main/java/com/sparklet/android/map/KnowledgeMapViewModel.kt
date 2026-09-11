package com.sparklet.android.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.MapResponse
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.MapApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KnowledgeMapViewModel(private val authSession: AuthSession) : ViewModel() {
    private val api = MapApi()

    private val _response = MutableStateFlow<MapResponse?>(null)
    val response: StateFlow<MapResponse?> = _response.asStateFlow()

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
                // Best-effort.
            } finally {
                _isLoading.value = false
            }
        }
    }
}
