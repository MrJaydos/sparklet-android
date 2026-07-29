package com.sparklet.android.model

import kotlinx.serialization.Serializable

@Serializable
enum class CardType {
    TEXT_IMAGE,
    VIDEO,
}

@Serializable
enum class DepthLevel {
    SIMPLE,
    STANDARD,
    DEEP,
    EXTRA_DEEP,
}

@Serializable
data class CardSource(
    val title: String,
    val publisher: String,
    val url: String,
)

@Serializable
data class RelatedLink(
    val id: String,
    val title: String,
    val icon: String,
)

// Mirrors FeedCard in sparklet/src/lib/feed.ts.
@Serializable
data class FeedCard(
    val id: String,
    val type: CardType,
    val title: String,
    val body: String,
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val sources: List<CardSource>,
    val readMoreUrl: String,
    val saved: Boolean,
    val seen: Boolean,
    val review: Boolean,
    val score: Int,
    val myVote: Int,
    val commentCount: Int,
    val depthLevel: DepthLevel,
    val category: Category,
    val createdAt: String,
    val related: List<RelatedLink>,
)

// Mirrors FeedQuiz. Not yet rendered/answered — see AGENTS.md's "still open" note.
@Serializable
data class FeedQuiz(
    val id: String,
    val question: String,
    val options: List<String>,
    val category: Category,
)

// Mirrors FeedReviewQuiz — same shape as FeedQuiz plus the source card it
// reviews; answering it hits /api/reviews/[id]/answer, not /api/quiz.
@Serializable
data class FeedReviewQuiz(
    val id: String,
    val question: String,
    val options: List<String>,
    val category: Category,
    val sourceCardId: String,
)

// Mirrors FeedGuess.
@Serializable
data class FeedGuess(
    val id: String,
    val prompt: String,
    val min: Double,
    val max: Double,
    val unit: String,
    val integer: Boolean,
    val category: Category,
)

// Mirrors FeedMisconception.
@Serializable
data class FeedMisconception(
    val id: String,
    val claim: String,
    val category: Category,
)

// Mirrors FeedExplainPrompt.
@Serializable
data class FeedExplainPrompt(
    val id: String,
    val title: String,
    val body: String,
    val category: Category,
)

// Mirrors the object shape returned by GET /api/feed (see
// sparklet/src/app/api/feed/route.ts -> getFeedCards).
@Serializable
data class FeedResponse(
    val cards: List<FeedCard>,
    val quizzes: List<FeedQuiz>,
    val reviewQuizzes: List<FeedReviewQuiz>,
    val guesses: List<FeedGuess>,
    val misconceptions: List<FeedMisconception>,
    val explainPrompts: List<FeedExplainPrompt>,
    val exhausted: Boolean,
)
