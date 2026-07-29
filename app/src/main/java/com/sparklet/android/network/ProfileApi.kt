package com.sparklet.android.network

import com.sparklet.android.model.ProfileResponse

class ProfileApi(private val client: ApiClient = ApiClient) {

    // The route falls back to a `sparklet.tz` cookie, which is a web-only
    // convention (see sparklet/src/app/api/profile/route.ts) — native
    // clients must always pass tz explicitly or the daily numbers silently
    // come back computed at tz=0.
    suspend fun fetchProfile(token: String?): ProfileResponse {
        return client.get(
            "api/profile",
            query = listOf("tz" to TimeZoneOffset.minutesWestOfUtc().toString()),
            token = token,
        )
    }
}
