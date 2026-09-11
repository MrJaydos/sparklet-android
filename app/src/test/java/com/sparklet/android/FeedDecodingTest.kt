package com.sparklet.android

import com.sparklet.android.model.FeedItem
import com.sparklet.android.model.FeedResponse
import com.sparklet.android.model.pagerKey
import com.sparklet.android.network.ApiClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Guards the /api/feed contract, which is consumed with no shared types
// package between the repos — a field renamed on the backend shows up here
// rather than as a blank feed on a device.
class FeedDecodingTest {

    private val feedJson = """
        {
          "cards": [{
            "id": "card1",
            "type": "TEXT_IMAGE",
            "title": "A title",
            "body": "A body",
            "imageUrl": null,
            "videoUrl": null,
            "sources": [{"title": "S", "publisher": "P", "url": "https://example.com"}],
            "readMoreUrl": "https://example.com",
            "saved": false,
            "seen": false,
            "review": false,
            "score": 3,
            "myVote": 0,
            "commentCount": 2,
            "depthLevel": "STANDARD",
            "category": {"slug": "space", "name": "Space", "colorHex": "#38bdf8", "icon": "🚀"},
            "createdAt": "2026-01-01T00:00:00.000Z",
            "related": []
          }],
          "quizzes": [],
          "reviewQuizzes": [],
          "guesses": [],
          "misconceptions": [],
          "explainPrompts": [],
          "exhausted": false
        }
    """.trimIndent()

    @Test
    fun `decodes a feed response`() {
        val response = ApiClient.json.decodeFromString<FeedResponse>(feedJson)
        val card = response.cards.single()
        assertEquals("card1", card.id)
        assertEquals(3, card.score)
        assertEquals(2, card.commentCount)
        assertEquals("space", card.category.slug)
        assertTrue(card.imageUrl == null)
        assertEquals(false, response.exhausted)
    }

    // Cards legitimately repeat in one session once the feed is exhausted and
    // starts recirculating. Compose rejects duplicate keys outright, so two
    // occurrences of the same card must not collide — this is the invariant
    // FeedItem.Card.occurrence exists to hold.
    @Test
    fun `repeated cards get distinct pager keys`() {
        val card = ApiClient.json.decodeFromString<FeedResponse>(feedJson).cards.single()
        val first = FeedItem.Card(card, occurrence = 0)
        val second = FeedItem.Card(card, occurrence = 7)

        assertEquals(first.id, second.id)
        assertNotEquals(
            "same card at two positions must not share a pager key",
            first.pagerKey,
            second.pagerKey,
        )
    }

    // Pager keys are namespaced by kind because nothing guarantees card and
    // quiz id spaces never collide.
    @Test
    fun `pager keys are namespaced by kind`() {
        val card = ApiClient.json.decodeFromString<FeedResponse>(feedJson).cards.single()
        assertTrue(FeedItem.Card(card, occurrence = 0).pagerKey.startsWith("card:"))
    }
}
