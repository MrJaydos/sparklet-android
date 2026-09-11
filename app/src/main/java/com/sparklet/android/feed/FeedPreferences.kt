package com.sparklet.android.feed

import android.content.Context
import com.sparklet.android.model.DepthLevel
import java.time.LocalDate

// Client-only feed preferences. Per AGENTS.md the daily card-count goal is
// deliberately local and never sent to the server (the web keeps it in
// localStorage), and the manual depth switch is the same kind of thing.
//
// SharedPreferences rather than DataStore, which this app already uses for
// the auth token: these are read synchronously during composition and during
// a goal-crossing check, and are single scalars with no migration story.
// DataStore's suspend/Flow API would mean plumbing async reads through both
// for no benefit. Key names match the web's localStorage keys for parity,
// even though the two stores are of course not shared.
class FeedPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sparklet.feed", Context.MODE_PRIVATE)

    var dailyCardGoal: Int
        get() = prefs.getInt(GOAL_KEY, 0).takeIf { it > 0 } ?: DEFAULT_DAILY_CARD_GOAL
        set(value) = prefs.edit().putInt(GOAL_KEY, value).apply()

    // null (never set) and STANDARD (explicitly reset) both mean "don't
    // auto-apply anything", matching the web's auto-apply guard.
    var depth: DepthLevel?
        get() = prefs.getString(DEPTH_KEY, null)?.let { raw ->
            runCatching { DepthLevel.valueOf(raw) }.getOrNull()
        }
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(DEPTH_KEY) else putString(DEPTH_KEY, value.name)
            }.apply()
        }

    // Mirrors the web's date-string guard: the goal-reached celebration fires
    // at most once per local calendar day, even if this is called again later
    // the same day — a second crossing-edge false positive, or the app
    // relaunching after the goal was already hit. Returns true only the first
    // time it's called on a given day.
    fun markGoalReachedIfNeededToday(): Boolean {
        val today = LocalDate.now().toString()
        if (prefs.getString(GOAL_HIT_KEY, null) == today) return false
        prefs.edit().putString(GOAL_HIT_KEY, today).apply()
        return true
    }

    // One-time swipe hint, mirroring Feed.tsx's. Persisted rather than
    // session-scoped: it's an onboarding affordance, not a recurring nudge.
    var hasSeenSwipeHint: Boolean
        get() = prefs.getBoolean(SWIPE_HINT_KEY, false)
        set(value) = prefs.edit().putBoolean(SWIPE_HINT_KEY, value).apply()

    // "Every other session" gating for the in-feed invite prompt, resolved
    // once per FeedViewModel lifetime — mirrors Feed.tsx's mount effect over
    // `sparklet.inviteSessionCount`. Increments on read, so each call is a
    // new session by definition.
    fun nextSessionShowsInvite(): Boolean {
        val next = prefs.getInt(INVITE_SESSION_COUNT_KEY, 0) + 1
        prefs.edit().putInt(INVITE_SESSION_COUNT_KEY, next).apply()
        return next % 2 == 0
    }

    companion object {
        const val DEFAULT_DAILY_CARD_GOAL = 10
        val DAILY_CARD_GOAL_OPTIONS = listOf(5, 10, 15, 20, 30)

        private const val GOAL_KEY = "sparklet.dailyGoal"
        private const val GOAL_HIT_KEY = "sparklet.goalHit"
        private const val DEPTH_KEY = "sparklet.depth"
        private const val INVITE_SESSION_COUNT_KEY = "sparklet.inviteSessionCount"
        private const val SWIPE_HINT_KEY = "sparklet.swipeHintSeen"
    }
}
