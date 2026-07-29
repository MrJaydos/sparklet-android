package com.sparklet.android.model

// Wraps every pager-page kind the feed can render. Cards come straight from
// FeedResponse.cards; the challenge kinds are interleaved into that stream
// client-side (see FeedViewModel's interleave()) rather than arriving
// pre-mixed from the server.
sealed class FeedItem {
    abstract val id: String

    data class Card(val card: FeedCard) : FeedItem() {
        override val id = card.id
    }

    data class Quiz(val quiz: FeedQuiz) : FeedItem() {
        override val id = quiz.id
    }

    data class ReviewQuiz(val quiz: FeedReviewQuiz) : FeedItem() {
        override val id = quiz.id
    }

    data class Guess(val guess: FeedGuess) : FeedItem() {
        override val id = guess.id
    }

    data class Misconception(val misconception: FeedMisconception) : FeedItem() {
        override val id = misconception.id
    }
}

// Pager page key: prefixed by kind rather than the bare id, since nothing
// guarantees quiz/guess/misconception/card id namespaces never collide.
val FeedItem.pagerKey: String
    get() = when (this) {
        is FeedItem.Card -> "card:$id"
        is FeedItem.Quiz -> "quiz:$id"
        is FeedItem.ReviewQuiz -> "reviewQuiz:$id"
        is FeedItem.Guess -> "guess:$id"
        is FeedItem.Misconception -> "misconception:$id"
    }
