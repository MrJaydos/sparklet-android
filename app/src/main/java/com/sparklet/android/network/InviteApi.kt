package com.sparklet.android.network

import com.sparklet.android.model.InviteResponse
import kotlinx.serialization.Serializable

// Mirrors POST /api/invite/[refId]/accept
// (sparklet/src/app/api/invite/[refId]/accept/route.ts).
class InviteApi(private val client: ApiClient = ApiClient) {

    // The route reads no body — an empty object is sent only because
    // ApiClient.post requires one.
    @Serializable
    private class EmptyBody

    suspend fun accept(refId: String, token: String?): InviteResponse =
        client.post("api/invite/$refId/accept", EmptyBody(), token)
}
