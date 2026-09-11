package com.sparklet.android.network

import com.sparklet.android.model.NotificationsResponse
import kotlinx.serialization.Serializable

// Mirrors GET/POST /api/notifications
// (sparklet/src/app/api/notifications/route.ts).
class NotificationsApi(private val client: ApiClient = ApiClient) {

    // The route doesn't read the request body — an empty object is sent
    // purely because ApiClient.post requires one.
    @Serializable
    private class EmptyBody

    @Serializable
    private data class MarkReadResponse(val ok: Boolean)

    suspend fun fetch(token: String?): NotificationsResponse =
        client.get("api/notifications", token = token)

    suspend fun markAllRead(token: String?) {
        client.post<EmptyBody, MarkReadResponse>("api/notifications", EmptyBody(), token)
    }
}
