package com.sparklet.android.network

import com.sparklet.android.model.ProfileDetailsResponse
import com.sparklet.android.model.ProfileResponse
import kotlinx.serialization.Serializable

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

    // A separate route from GET /api/profile: that one is polled on every
    // feed load, while this (badges/history/notebook/top categories) is only
    // needed when the Profile screen itself opens.
    suspend fun fetchDetails(token: String?): ProfileDetailsResponse =
        client.get("api/profile/details", token = token)

    @Serializable
    private data class UpdateNameRequest(val name: String)

    @Serializable
    private data class UpdateNameResponse(val ok: Boolean, val name: String? = null)

    suspend fun updateName(name: String, token: String?) {
        client.patch<UpdateNameRequest, UpdateNameResponse>(
            path = "api/profile",
            body = UpdateNameRequest(name),
            token = token,
        )
    }
}
