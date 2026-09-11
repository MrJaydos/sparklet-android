package com.sparklet.android.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors POST /api/cards/[id]/vote's response.
@Serializable
data class VoteResponse(val score: Int, val myVote: Int)

// Mirrors POST /api/cards/[id]/save's response.
@Serializable
data class SaveResponse(val saved: Boolean)

// Mirrors a comment object from GET/POST /api/cards/[id]/comments.
@Serializable
data class Comment(
    val id: String,
    val body: String,
    val createdAt: String,
    val author: String,
    val mine: Boolean,
)

@Serializable
data class CommentsResponse(val comments: List<Comment>)

@Serializable
data class PostCommentResponse(val comment: Comment)

// Mirrors POST /api/report's body reason enum. Labels match ReportSheet.tsx's
// REASONS copy exactly.
@Serializable
enum class ReportReason(val label: String) {
    @SerialName("INCORRECT")
    INCORRECT("❌ Factually incorrect"),

    @SerialName("INAPPROPRIATE")
    INAPPROPRIATE("⚠️ Inappropriate"),

    @SerialName("SPAM")
    SPAM("🗑️ Spam"),

    @SerialName("OTHER")
    OTHER("💬 Something else"),
}

@Serializable
data class ReportResponse(val ok: Boolean, val alreadyReported: Boolean? = null)

// Mirrors POST /api/cards/[id]/depth's response. The returned variant's own
// `id` is deliberately unused client-side: the web only swaps the displayed
// title/body, while every action (vote/save/comment/report) keeps targeting
// the original standard card's id.
@Serializable
data class DepthCardResponse(
    val card: DepthCardVariant,
    val generated: Boolean,
)

@Serializable
data class DepthCardVariant(
    val id: String,
    val title: String,
    val body: String,
    val depthLevel: DepthLevel,
)
