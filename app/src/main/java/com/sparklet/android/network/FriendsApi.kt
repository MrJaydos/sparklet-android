package com.sparklet.android.network

import com.sparklet.android.model.FriendsResponse
import kotlinx.serialization.Serializable

// Mirrors GET/POST /api/friends and PATCH/DELETE /api/friends/[id]
// (sparklet/src/app/api/friends/route.ts, .../[id]/route.ts).
class FriendsApi(private val client: ApiClient = ApiClient) {

    // The route accepts exactly one of email/code (a zod union). Same
    // "@ means email, otherwise treat it as a friend code" split the web
    // client's FriendsPanel.sendRequest uses, so one text field covers both.
    // Note both fields are nullable and nulls are omitted on the wire
    // (ApiClient's explicitNulls = false), which is what keeps this a valid
    // union member rather than an object with an explicit null.
    @Serializable
    private data class RequestBody(val email: String? = null, val code: String? = null)

    @Serializable
    data class SendRequestResponse(val ok: Boolean, val message: String)

    suspend fun fetch(token: String?): FriendsResponse =
        client.get("api/friends", token = token)

    suspend fun sendRequest(value: String, token: String?): SendRequestResponse {
        val body = if (value.contains("@")) RequestBody(email = value) else RequestBody(code = value)
        return client.post("api/friends", body, token)
    }

    suspend fun accept(friendshipId: String, token: String?) {
        client.patchDiscardingResponse("api/friends/$friendshipId", token)
    }

    // Declining a pending request, cancelling one you sent, and unfriending
    // an accepted one are all the same call — the route deletes the row
    // either way, same as the web client's `remove`.
    suspend fun remove(friendshipId: String, token: String?) {
        client.delete("api/friends/$friendshipId", token)
    }
}
