package com.sparklet.android.model

import kotlinx.serialization.Serializable

// Mirrors POST /api/quiz/[id]/answer's response, and POST
// /api/reviews/[id]/answer's (same shape minus sourceCardId, hence nullable).
@Serializable
data class QuizAnswerResponse(
    val correct: Boolean,
    val correctIndex: Int,
    val explanation: String,
    val sourceCardId: String? = null,
    val xp: XpSummary,
    val combo: Int,
    val multiplier: Double,
    val guest: Boolean = false,
)

// Mirrors POST /api/guess/[id]/answer's response.
@Serializable
data class GuessAnswerResponse(
    val answer: Double,
    val accuracy: Double,
    val correct: Boolean,
    val explanation: String,
    val sourceCardId: String,
    val xp: XpSummary,
    val combo: Int,
    val multiplier: Double,
    val guest: Boolean = false,
)

// Mirrors POST /api/misconception/[id]/answer's response.
@Serializable
data class MisconceptionAnswerResponse(
    val answer: Boolean,
    val correct: Boolean,
    val explanation: String,
    val sourceCardId: String,
    val xp: XpSummary,
    val combo: Int,
    val multiplier: Double,
    val guest: Boolean = false,
)

// Mirrors POST /api/explain/[cardId]/answer. `score` is the grader's 0-1
// rating of the explanation; `feedback` is its prose response.
@Serializable
data class ExplainAnswerResponse(
    val score: Double,
    val feedback: String,
    val xp: XpSummary,
)
