package com.sparklet.android.network

import com.sparklet.android.model.GuessAnswerResponse
import com.sparklet.android.model.ExplainAnswerResponse
import com.sparklet.android.model.MisconceptionAnswerResponse
import com.sparklet.android.model.QuizAnswerResponse
import kotlinx.serialization.Serializable

@Serializable
private data class QuizAnswerRequest(val index: Int, val tzOffsetMinutes: Int)

@Serializable
private data class GuessAnswerRequest(val guess: Double, val tzOffsetMinutes: Int)

@Serializable
private data class MisconceptionAnswerRequest(val guess: Boolean, val tzOffsetMinutes: Int)

// Exactly one of text/skip is sent; the unused one is omitted on the wire
// (ApiClient's explicitNulls = false), matching the route's zod union.
@Serializable
internal data class ExplainAnswerRequest(
    val text: String? = null,
    val skip: Boolean? = null,
    val tzOffsetMinutes: Int,
)

// Answer endpoints for the four interactive feed challenge kinds. Unlike
// FeedApi's read-tracking flow, XP here comes back directly on the answer
// response — no separate two-POST dwell gate, since committing to an
// answer *is* the completed action (see each backend route handler).
class ChallengeApi(private val client: ApiClient = ApiClient) {

    suspend fun answerQuiz(id: String, index: Int, token: String?): QuizAnswerResponse {
        val body = QuizAnswerRequest(index = index, tzOffsetMinutes = TimeZoneOffset.minutesWestOfUtc())
        return client.post("api/quiz/$id/answer", body, token)
    }

    // Same request/response shape as answerQuiz, but a due spaced-repetition
    // review can 409 ("not due") if the server no longer considers the
    // source card due by the time this posts — callers must handle that.
    suspend fun answerReviewQuiz(id: String, index: Int, token: String?): QuizAnswerResponse {
        val body = QuizAnswerRequest(index = index, tzOffsetMinutes = TimeZoneOffset.minutesWestOfUtc())
        return client.post("api/reviews/$id/answer", body, token)
    }

    suspend fun answerGuess(id: String, guess: Double, token: String?): GuessAnswerResponse {
        val body = GuessAnswerRequest(guess = guess, tzOffsetMinutes = TimeZoneOffset.minutesWestOfUtc())
        return client.post("api/guess/$id/answer", body, token)
    }

    suspend fun answerMisconception(id: String, guess: Boolean, token: String?): MisconceptionAnswerResponse {
        val body = MisconceptionAnswerRequest(guess = guess, tzOffsetMinutes = TimeZoneOffset.minutesWestOfUtc())
        return client.post("api/misconception/$id/answer", body, token)
    }

    // Explain-back prompts are graded by the backend rather than scored
    // against a fixed answer, so the response carries prose feedback as well
    // as XP. Skipping still posts — the route records the skip so the prompt
    // isn't handed out again.
    suspend fun answerExplain(cardId: String, text: String, token: String?): ExplainAnswerResponse {
        val body = ExplainAnswerRequest(
            text = text,
            tzOffsetMinutes = TimeZoneOffset.minutesWestOfUtc(),
        )
        return client.post("api/explain/$cardId/answer", body, token)
    }

    suspend fun skipExplain(cardId: String, token: String?): ExplainAnswerResponse {
        val body = ExplainAnswerRequest(
            skip = true,
            tzOffsetMinutes = TimeZoneOffset.minutesWestOfUtc(),
        )
        return client.post("api/explain/$cardId/answer", body, token)
    }
}
