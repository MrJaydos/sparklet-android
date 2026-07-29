package com.sparklet.android.model

import kotlinx.serialization.Serializable

// Mirrors StreakSummary embedded in XpSummary (sparklet/src/lib/xp.ts).
@Serializable
data class StreakSummary(
    val currentStreak: Int,
    val longestStreak: Int,
    val freezesUsed: Int,
    val freezesAvailable: Int,
)

// Mirrors XpSummary (sparklet/src/lib/xp.ts). streak is present only on the
// action that pushes today's completions past STREAK_MIN_CARDS.
@Serializable
data class XpSummary(
    val awarded: Int,
    val today: Int,
    val total: Int,
    val goal: Int,
    val streak: StreakSummary? = null,
)

// Mirrors the POST /api/interactions response body
// (sparklet/src/app/api/interactions/route.ts).
@Serializable
data class InteractionResponse(
    val ok: Boolean,
    val streak: StreakSummary? = null,
    val xp: XpSummary,
    val read: Boolean,
)
