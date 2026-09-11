package com.sparklet.android

import com.sparklet.android.network.ApiClient
import com.sparklet.android.network.ExplainAnswerRequest
import com.sparklet.android.network.InteractionRequest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Regression tests for the wire shape of request bodies, which is load-bearing
// against the backend's zod schemas and has silently broken once already.
//
// Read tracking was dead for the life of this client because `action` carried
// a default value and kotlinx.serialization omits defaults unless
// encodeDefaults is set, so every /api/interactions POST 400'd — and
// FeedViewModel.trackView swallows exceptions by design, so nothing surfaced.
// These assert the encoder configuration itself, not just the model, because
// the bug lived in the configuration.
class RequestSerializationTest {

    @Test
    fun `interaction body includes action`() {
        val json = ApiClient.json.encodeToString(
            InteractionRequest(cardId = "card1", action = "view", tzOffsetMinutes = -600)
        )
        assertTrue("action must reach the wire: $json", json.contains("\"action\":\"view\""))
        assertTrue(json.contains("\"cardId\":\"card1\""))
        assertTrue(json.contains("\"tzOffsetMinutes\":-600"))
    }

    // zod's .optional() accepts `undefined` but rejects an explicit null, so a
    // null must be absent rather than serialized — otherwise the entry-view
    // POST (which sends no dwellMs) 400s.
    @Test
    fun `null dwellMs is omitted rather than sent as null`() {
        val json = ApiClient.json.encodeToString(
            InteractionRequest(cardId = "card1", action = "view", tzOffsetMinutes = 0, dwellMs = null)
        )
        assertFalse("null must not be serialized: $json", json.contains("dwellMs"))
    }

    @Test
    fun `dwellMs is sent when present`() {
        val json = ApiClient.json.encodeToString(
            InteractionRequest(cardId = "card1", action = "view", tzOffsetMinutes = 0, dwellMs = 5_000)
        )
        assertTrue(json.contains("\"dwellMs\":5000"))
    }

    // The explain answer route takes a zod union of text-or-skip; sending both
    // keys, one of them null, matches neither member.
    @Test
    fun `explain answer sends exactly one of text and skip`() {
        val answered = ApiClient.json.encodeToString(
            ExplainAnswerRequest(text = "because entropy", tzOffsetMinutes = 0)
        )
        assertTrue(answered.contains("\"text\":\"because entropy\""))
        assertFalse("skip must be absent when answering: $answered", answered.contains("skip"))

        val skipped = ApiClient.json.encodeToString(
            ExplainAnswerRequest(skip = true, tzOffsetMinutes = 0)
        )
        assertTrue(skipped.contains("\"skip\":true"))
        assertFalse("text must be absent when skipping: $skipped", skipped.contains("text"))
    }

    // Unknown keys must be ignored on decode: the backend adds response fields
    // without a shared types package between the repos, and a strict decoder
    // would turn any such addition into a crash in the field.
    @Test
    fun `decoding tolerates unknown keys`() {
        val decoded = ApiClient.json.decodeFromString<InteractionRequest>(
            """{"cardId":"c","action":"view","tzOffsetMinutes":0,"somethingNew":true}"""
        )
        assertEquals("c", decoded.cardId)
    }
}
