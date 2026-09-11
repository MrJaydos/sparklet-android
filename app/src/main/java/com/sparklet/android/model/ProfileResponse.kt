package com.sparklet.android.model

import kotlinx.serialization.Serializable

// Mirrors GET /api/profile (sparklet/src/app/api/profile/route.ts, added
// specifically to give native clients a JSON source for the XP ring/streak
// state). Two distinct daily numbers, kept separate on purpose (see
// AGENTS.md): xpToday/xpGoal answer "did I hit my XP today", cardsToday
// answers "did I hit my count today".
@Serializable
data class ProfileResponse(
    val id: String,
    val xp: Int,
    val xpToday: Int,
    val xpGoal: Int,
    val cardsToday: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val freezesAvailable: Int,
    // Same one-time "first session, never onboarded" condition as the web's
    // feed-page redirect (sparklet/src/app/feed/page.tsx), computed
    // server-side. This client has no server-driven redirect to hook into, so
    // the feed shows the onboarding sheet on it instead. Defaulted so an
    // older backend that doesn't send it can't break decoding.
    val needsOnboarding: Boolean = false,
)
