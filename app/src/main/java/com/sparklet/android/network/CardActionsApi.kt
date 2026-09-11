package com.sparklet.android.network

import com.sparklet.android.model.Comment
import com.sparklet.android.model.CommentsResponse
import com.sparklet.android.model.DepthCardResponse
import com.sparklet.android.model.DepthLevel
import com.sparklet.android.model.FeedCard
import com.sparklet.android.model.PostCommentResponse
import com.sparklet.android.model.ReportReason
import com.sparklet.android.model.ReportResponse
import com.sparklet.android.model.SaveResponse
import com.sparklet.android.model.VoteResponse
import kotlinx.serialization.Serializable

// Mirrors sparklet/src/app/api/cards/[id]/{vote,save,comments,depth}/route.ts
// and /api/report/route.ts.
class CardActionsApi(private val client: ApiClient = ApiClient) {

    @Serializable
    private data class VoteRequest(val value: Int)

    @Serializable
    private data class SaveRequest(val saved: Boolean)

    @Serializable
    private data class PostCommentRequest(val body: String)

    // Exactly one of cardId/commentId may be set — the backend's zod .refine
    // rejects both or neither. Nulls are omitted on the wire (ApiClient's
    // explicitNulls = false), which is what makes the unused one absent
    // rather than an explicit null.
    @Serializable
    private data class ReportRequest(
        val cardId: String? = null,
        val commentId: String? = null,
        val reason: ReportReason,
        val detail: String? = null,
    )

    @Serializable
    private data class DepthRequest(val level: DepthLevel)

    // Single-card lookup, returned in the same FeedCard shape /api/feed uses,
    // so it decodes straight into the existing model.
    suspend fun fetchCard(cardId: String, token: String?): FeedCard =
        client.get("api/cards/$cardId", token = token)

    suspend fun vote(cardId: String, value: Int, token: String?): VoteResponse =
        client.post("api/cards/$cardId/vote", VoteRequest(value), token)

    suspend fun setSaved(cardId: String, saved: Boolean, token: String?): SaveResponse =
        client.post("api/cards/$cardId/save", SaveRequest(saved), token)

    suspend fun fetchComments(cardId: String, token: String?): CommentsResponse =
        client.get("api/cards/$cardId/comments", token = token)

    suspend fun postComment(cardId: String, body: String, token: String?): Comment =
        client.post<PostCommentRequest, PostCommentResponse>(
            "api/cards/$cardId/comments",
            PostCommentRequest(body),
            token,
        ).comment

    suspend fun reportCard(
        cardId: String,
        reason: ReportReason,
        detail: String?,
        token: String?,
    ): ReportResponse =
        client.post("api/report", ReportRequest(cardId = cardId, reason = reason, detail = detail), token)

    suspend fun reportComment(
        commentId: String,
        reason: ReportReason,
        detail: String?,
        token: String?,
    ): ReportResponse =
        client.post("api/report", ReportRequest(commentId = commentId, reason = reason, detail = detail), token)

    // Throws ApiException.Server(402) when the level is premium-gated and the
    // caller isn't subscribed — callers must handle that rather than treating
    // it as a generic failure.
    suspend fun fetchDepth(cardId: String, level: DepthLevel, token: String?): DepthCardResponse =
        client.post("api/cards/$cardId/depth", DepthRequest(level), token)
}
