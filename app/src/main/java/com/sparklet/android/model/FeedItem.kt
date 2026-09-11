package com.sparklet.android.model

// Wraps every pager-page kind the feed can render. Cards come straight from
// FeedResponse.cards; the challenge kinds are interleaved into that stream
// client-side (see FeedViewModel's interleave()) rather than arriving
// pre-mixed from the server.
sealed class FeedItem {
    abstract val id: String

    // `occurrence` is the card's index in FeedViewModel's cumulative `cards`
    // pool at the moment it was interleaved in. Cards legitimately repeat
    // within one session once the account runs out of unseen content and the
    // feed starts recirculating (see FeedViewModel's exhausted/allowRepeats
    // handling), so the same card.id can appear at two positions in `items`.
    // Without this the two would produce an identical pager key, which
    // Compose rejects outright — and would also misattribute read-tracking
    // between the two occurrences. The index is stable across rebuilds
    // because `cards` only ever grows by appending.
    data class Card(val card: FeedCard, val occurrence: Int) : FeedItem() {
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

    data class Explain(val prompt: FeedExplainPrompt) : FeedItem() {
        override val id = prompt.id
    }

    // The three below have no server-side model at all — they're
    // session-recap/growth slides inserted purely client-side, mirroring
    // Feed.tsx's `{ kind: "checkin", afterCount }` and friends. `afterCount`
    // is only used for id uniqueness; the copy reads live session state from
    // the view model rather than this frozen snapshot.
    data class Checkin(val afterCount: Int) : FeedItem() {
        override val id = "checkin-$afterCount"
    }

    data object Invite : FeedItem() {
        override val id = "invite"
    }

    data object GoalReached : FeedItem() {
        override val id = "goalReached"
    }
}

// Pager page key: prefixed by kind rather than the bare id, since nothing
// guarantees quiz/guess/misconception/card id namespaces never collide.
val FeedItem.pagerKey: String
    get() = when (this) {
        is FeedItem.Card -> "card:$id:$occurrence"
        is FeedItem.Quiz -> "quiz:$id"
        is FeedItem.ReviewQuiz -> "reviewQuiz:$id"
        is FeedItem.Guess -> "guess:$id"
        is FeedItem.Misconception -> "misconception:$id"
        is FeedItem.Explain -> "explain:$id"
        is FeedItem.Checkin -> "checkin:$id"
        is FeedItem.Invite -> "invite"
        is FeedItem.GoalReached -> "goalReached"
    }
