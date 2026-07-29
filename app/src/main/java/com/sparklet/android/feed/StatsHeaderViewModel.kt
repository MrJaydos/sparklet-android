package com.sparklet.android.feed

import androidx.lifecycle.ViewModel
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.ProfileResponse
import com.sparklet.android.model.XpSummary
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.ProfileApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StatsHeaderViewModel(private val authSession: AuthSession) : ViewModel() {
    private val api = ProfileApi()

    private val _profile = MutableStateFlow<ProfileResponse?>(null)
    val profile: StateFlow<ProfileResponse?> = _profile.asStateFlow()

    suspend fun load() {
        try {
            _profile.value = api.fetchProfile(token = authSession.token.value)
        } catch (e: ApiException.Unauthorized) {
            authSession.signOut()
        } catch (e: Exception) {
            // Best-effort: the feed itself still works without the header.
        }
    }

    // Updates the header from a read POST's own response instead of
    // re-fetching /api/profile after every card — XpSummary already carries
    // today's/lifetime totals and the streak delta. cardsToday isn't in
    // XpSummary, but the backend's own invariant (one XpEvent row per
    // positive award — see xp.ts) means it advances exactly when
    // awarded > 0 does, so it's derived rather than stale until the next
    // full load.
    fun apply(xp: XpSummary) {
        val current = _profile.value ?: return
        // On the no-award path (already-completed card, rate-limited read),
        // interactions/route.ts hardcodes total: 0 rather than the real
        // lifetime sum — only today/goal are meaningful there. Falling back
        // to the last known xp avoids clobbering it to zero.
        _profile.value = ProfileResponse(
            xp = if (xp.awarded > 0) xp.total else current.xp,
            xpToday = xp.today,
            xpGoal = xp.goal,
            cardsToday = current.cardsToday + if (xp.awarded > 0) 1 else 0,
            currentStreak = xp.streak?.currentStreak ?: current.currentStreak,
            longestStreak = xp.streak?.longestStreak ?: current.longestStreak,
            freezesAvailable = xp.streak?.freezesAvailable ?: current.freezesAvailable,
        )
    }
}
