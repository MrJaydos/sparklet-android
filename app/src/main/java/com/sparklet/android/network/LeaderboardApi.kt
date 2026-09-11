package com.sparklet.android.network

import com.sparklet.android.model.LeaderboardResponse

// Mirrors GET /api/leaderboard (sparklet/src/app/api/leaderboard/route.ts).
class LeaderboardApi(private val client: ApiClient = ApiClient) {

    // Same tz convention as ProfileApi — the route needs it explicitly,
    // since its cookie fallback is web-only. Without it the "today" and
    // "7 days" boards are computed against UTC midnight rather than the
    // user's own day.
    suspend fun fetch(board: String, token: String?): LeaderboardResponse {
        return client.get(
            "api/leaderboard",
            query = listOf(
                "board" to board,
                "tz" to TimeZoneOffset.minutesWestOfUtc().toString(),
            ),
            token = token,
        )
    }
}
