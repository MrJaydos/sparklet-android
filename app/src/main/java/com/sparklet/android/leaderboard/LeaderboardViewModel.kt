package com.sparklet.android.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.LeaderboardResponse
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.LeaderboardApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LeaderboardViewModel(private val authSession: AuthSession) : ViewModel() {
    // rawValue must match the `board` query param the route accepts.
    enum class Board(val rawValue: String, val label: String) {
        TODAY("today", "Today"),
        WEEK("week", "7 days"),
        ALL("all", "All time"),
        FRIENDS("friends", "Friends"),
    }

    private val api = LeaderboardApi()

    private val _response = MutableStateFlow<LeaderboardResponse?>(null)
    val response: StateFlow<LeaderboardResponse?> = _response.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _board = MutableStateFlow(Board.TODAY)
    val board: StateFlow<Board> = _board.asStateFlow()

    fun selectBoard(board: Board) {
        if (_board.value == board) return
        _board.value = board
        // Clear rather than leave the previous board's rows on screen under a
        // newly-selected chip — they'd read as this board's results.
        _response.value = null
        load()
    }

    fun loadIfNeeded() {
        if (_response.value == null) load()
    }

    private fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val requested = _board.value
            try {
                val result = api.fetch(board = requested.rawValue, token = authSession.token.value)
                // Drop a response whose board the user has since switched
                // away from, so a slow request can't overwrite a faster one.
                if (_board.value == requested) _response.value = result
            } catch (e: ApiException.Unauthorized) {
                authSession.signOut()
            } catch (e: Exception) {
                // Best-effort: the board shows whatever loaded, if anything.
            } finally {
                _isLoading.value = false
            }
        }
    }
}
