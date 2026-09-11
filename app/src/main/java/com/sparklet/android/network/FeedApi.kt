package com.sparklet.android.network

import com.sparklet.android.model.FeedResponse
import com.sparklet.android.model.InteractionResponse
import kotlinx.serialization.Serializable

// Mirrors POST /api/interactions's body (sparklet/src/app/api/interactions/route.ts).
@Serializable
// `action` deliberately carries no default: the backend's zod schema requires
// it, and a default here is exactly what made it vanish from the wire once
// (see ApiClient.json). Passing it explicitly at the call site keeps the
// requirement visible rather than resting on serializer configuration.
private data class InteractionRequest(
    val cardId: String,
    val action: String,
    val tzOffsetMinutes: Int,
    val dwellMs: Int? = null,
)

class FeedApi(private val client: ApiClient = ApiClient) {

    suspend fun fetchFeed(
        categorySlugs: List<String> = emptyList(),
        take: Int = 10,
        allowRepeats: Boolean = false,
        excludeIds: List<String> = emptyList(),
        token: String?,
    ): FeedResponse {
        val query = buildList {
            add("take" to take.toString())
            if (categorySlugs.isNotEmpty()) add("categories" to categorySlugs.joinToString(","))
            if (allowRepeats) add("allowRepeats" to "1")
            if (excludeIds.isNotEmpty()) add("exclude" to excludeIds.joinToString(","))
        }
        return client.get("api/feed", query, token)
    }

    // dwellMs is sent for the server's own bookkeeping, but never trusted as
    // proof of a read — the backend gates "completed" on the gap between
    // this call and a prior /api/interactions POST for the same card,
    // measured by its own clock (MIN_READ_GAP_MS in interactions/route.ts).
    // Callers must issue the entry-view POST first, wait for the real
    // dwell, then send this one — there is no client-side shortcut.
    suspend fun postInteraction(
        cardId: String,
        dwellMs: Int? = null,
        token: String?,
    ): InteractionResponse {
        val body = InteractionRequest(
            cardId = cardId,
            action = "view",
            tzOffsetMinutes = TimeZoneOffset.minutesWestOfUtc(),
            dwellMs = dwellMs,
        )
        return client.post("api/interactions", body, token)
    }
}
