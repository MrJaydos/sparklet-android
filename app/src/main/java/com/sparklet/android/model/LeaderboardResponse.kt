package com.sparklet.android.model

import kotlinx.serialization.Serializable

// Mirrors GET /api/leaderboard (sparklet/src/app/api/leaderboard/route.ts),
// which ports the ranking logic straight out of the web's
// src/app/leaderboard/page.tsx — that page had no API route backing it.
@Serializable
data class LeaderboardResponse(
    val board: String,
    val rows: List<LeaderboardRow>,
    // Absent when the viewer has no XP on this board at all.
    val me: LeaderboardSelf? = null,
    // Whether the viewer already appears in `rows`; when false and `me` is
    // present, the viewer's own rank is pinned separately below the list.
    val inTop: Boolean,
    val selfName: String,
    val viewerId: String,
)

@Serializable
data class LeaderboardRow(
    val userId: String,
    val name: String,
    val xp: Int,
)

@Serializable
data class LeaderboardSelf(
    val xp: Int,
    val rank: Int,
)
