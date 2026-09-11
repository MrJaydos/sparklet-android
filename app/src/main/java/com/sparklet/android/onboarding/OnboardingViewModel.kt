package com.sparklet.android.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.Category
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.OnboardingApi
import com.sparklet.android.network.ProfileApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val authSession: AuthSession) : ViewModel() {
    private val api = OnboardingApi()
    private val profileApi = ProfileApi()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _selectedSlugs = MutableStateFlow<Set<String>>(emptySet())
    val selectedSlugs: StateFlow<Set<String>> = _selectedSlugs.asStateFlow()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    fun loadCategories() {
        if (_categories.value.isNotEmpty()) return
        viewModelScope.launch {
            try {
                _categories.value = api.fetchCategories(authSession.token.value)
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                // Best-effort — the grid stays empty and Skip still completes
                // onboarding either way.
            }
        }
    }

    fun setName(value: String) {
        _name.value = value
    }

    fun toggle(slug: String) {
        _selectedSlugs.value =
            if (slug in _selectedSlugs.value) _selectedSlugs.value - slug
            else _selectedSlugs.value + slug
    }

    // Same order as the web's OnboardingGrid.submit: save the name first (if
    // any), then the picks — or an empty array for "skip, show me
    // everything", which still completes onboarding server-side.
    //
    // Errors are swallowed rather than surfaced, matching the web's
    // `finally { router.push("/feed") }`: this is a one-time, low-stakes set
    // of preferences the user can redo from feed settings at any time, and
    // blocking entry to the app on it would be worse than losing it.
    fun complete(skip: Boolean, onDone: () -> Unit) {
        if (_isSubmitting.value) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val trimmed = _name.value.trim()
                if (trimmed.isNotEmpty()) {
                    profileApi.updateName(trimmed, authSession.token.value)
                }
                api.submitInterests(
                    categorySlugs = if (skip) emptyList() else _selectedSlugs.value.toList(),
                    token = authSession.token.value,
                )
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                // See doc comment.
            } finally {
                _isSubmitting.value = false
                onDone()
            }
        }
    }
}
