package com.sparklet.android.feed

import androidx.lifecycle.ViewModel
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.FeedCard
import com.sparklet.android.model.FeedGuess
import com.sparklet.android.model.FeedItem
import com.sparklet.android.model.FeedMisconception
import com.sparklet.android.model.FeedQuiz
import com.sparklet.android.model.FeedReviewQuiz
import com.sparklet.android.model.XpSummary
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.FeedApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FeedViewModel(private val authSession: AuthSession) : ViewModel() {
    private val api = FeedApi()

    // The single source of truth the pager renders from — plain cards
    // interleaved with quiz/guess/misconception/review-quiz challenges (see
    // interleaveBatch). FeedScreen reads only this, never a raw card list,
    // so pager indices can't desync between the two.
    private val _items = MutableStateFlow<List<FeedItem>>(emptyList())
    val items: StateFlow<List<FeedItem>> = _items.asStateFlow()

    // Plain cards seen so far, kept only for excludeIds bookkeeping on the
    // next fetch (see loadMoreIfNeeded) — not exposed, FeedScreen renders
    // from `items`.
    private var cards = emptyList<FeedCard>()

    // Unspent challenge pools plus cursors into them — mirrors Feed.tsx's
    // quizCursor/guessCursor/misconceptionCursor. A pool can outlive a
    // single batch (a quiz fetched but not yet due for insertion stays
    // queued), so appending on loadMoreIfNeeded and only resetting on a
    // full load() is deliberate, not an oversight.
    private var quizzes = emptyList<FeedQuiz>()
    private var quizCursor = 0
    private var guesses = emptyList<FeedGuess>()
    private var guessCursor = 0
    private var misconceptions = emptyList<FeedMisconception>()
    private var misconceptionCursor = 0

    // Running count of FeedCards folded into `items` so far, across the
    // whole session. The _EVERY pacing constants below are defined against
    // this count, not items.size — items.size also counts the inserted
    // challenge entries, so it outpaces the card stream the constants were
    // tuned for.
    private var cardsConsumed = 0

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    suspend fun loadIfNeeded() {
        if (_items.value.isEmpty()) load()
    }

    // Initial load and pull-to-refresh both replace the batch outright — the
    // feed is server-composed and stateless per request (sparklet/
    // src/lib/feed.ts), so "refresh" means "give me a fresh read," not
    // "append more." Every pool/cursor resets here for the same reason:
    // carrying stale offsets into a freshly-fetched batch would desync the
    // interleave pacing immediately after refresh.
    suspend fun load() {
        _isLoading.value = true
        _errorMessage.value = null
        try {
            val response = api.fetchFeed(take = 10, token = authSession.token.value)
            cards = response.cards
            quizzes = response.quizzes
            quizCursor = 0
            guesses = response.guesses
            guessCursor = 0
            misconceptions = response.misconceptions
            misconceptionCursor = 0
            cardsConsumed = 0
            _items.value = emptyList()
            interleaveBatch(response.cards, response.reviewQuizzes)
        } catch (e: ApiException.Unauthorized) {
            authSession.signOut()
        } catch (e: Exception) {
            _errorMessage.value = "Couldn't load the feed. Pull to retry."
        } finally {
            _isLoading.value = false
        }
    }

    // Pagination: appends the next batch once the settled page nears the end
    // of what's loaded. Takes the settled page's raw index into `items`
    // rather than looking up a card id, since the settled page may be a
    // challenge item that was never in `cards` to begin with.
    suspend fun loadMoreIfNeeded(pageIndex: Int, totalItems: Int) {
        if (_isLoading.value) return
        if (pageIndex < totalItems - 2) return

        _isLoading.value = true
        try {
            // A review-quiz's underlying card lives outside `cards` (it's a
            // separate FeedItem kind), so it needs its own exclude entry —
            // otherwise a skipped, unanswered review-quiz can reappear on
            // the very next batch since its due state never changed.
            val excludeIds = cards.map { it.id } +
                _items.value.filterIsInstance<FeedItem.ReviewQuiz>().map { it.quiz.sourceCardId }
            val response = api.fetchFeed(
                take = 10,
                excludeIds = excludeIds,
                token = authSession.token.value,
            )
            cards = cards + response.cards

            // The server doesn't know which quizzes/guesses/misconceptions
            // it already handed out that are still unanswered (only
            // *answered* ones are excluded via `attempts: none`), so a
            // repeat batch fetch can resend one already queued here —
            // dedupe by id before appending.
            val knownQuizIds = quizzes.map { it.id }.toSet()
            quizzes = quizzes + response.quizzes.filter { it.id !in knownQuizIds }
            val knownGuessIds = guesses.map { it.id }.toSet()
            guesses = guesses + response.guesses.filter { it.id !in knownGuessIds }
            val knownMisconceptionIds = misconceptions.map { it.id }.toSet()
            misconceptions = misconceptions + response.misconceptions.filter { it.id !in knownMisconceptionIds }
            val knownReviewQuizIds =
                _items.value.filterIsInstance<FeedItem.ReviewQuiz>().map { it.quiz.id }.toSet()
            val newReviewQuizzes = response.reviewQuizzes.filter { it.id !in knownReviewQuizIds }

            interleaveBatch(response.cards, newReviewQuizzes)
        } catch (e: ApiException.Unauthorized) {
            authSession.signOut()
        } catch (e: Exception) {
            // Best-effort — the user can still scroll what's already loaded.
        } finally {
            _isLoading.value = false
        }
    }

    // Walks a newly-arrived batch of cards, inserting this batch's
    // review-quizzes (spread evenly across just these cards, mirroring the
    // server's own interleave() for plain review cards) and consuming
    // quiz/guess/misconception entries at fixed intervals, then appends the
    // result to `items`. Interval constants and the modulo formula are
    // ported verbatim from sparklet's Feed.tsx (QUIZ_EVERY/GUESS_EVERY/
    // MISCONCEPTION_EVERY) — chosen so no two challenge kinds ever land on
    // the same card position, "verified with no collisions across 600
    // simulated positions" per that source's comment.
    private fun interleaveBatch(newCards: List<FeedCard>, reviewQuizzesInBatch: List<FeedReviewQuiz>) {
        val out = mutableListOf<FeedItem>()
        var reviewQuizCursor = 0
        fun reviewQuizAt(i: Int) =
            Math.round(((i + 1).toDouble() * newCards.size) / (reviewQuizzesInBatch.size + 1)).toInt()
        fun flushReviewQuizzesUpTo(index: Int) {
            while (reviewQuizCursor < reviewQuizzesInBatch.size && reviewQuizAt(reviewQuizCursor) <= index) {
                out += FeedItem.ReviewQuiz(reviewQuizzesInBatch[reviewQuizCursor])
                reviewQuizCursor++
            }
        }

        newCards.forEachIndexed { i, card ->
            flushReviewQuizzesUpTo(i)
            out += FeedItem.Card(card)
            cardsConsumed++
            if (cardsConsumed % QUIZ_EVERY == 0 && quizCursor < quizzes.size) {
                out += FeedItem.Quiz(quizzes[quizCursor++])
            }
            if (cardsConsumed % GUESS_EVERY == GUESS_OFFSET && guessCursor < guesses.size) {
                out += FeedItem.Guess(guesses[guessCursor++])
            }
            if (cardsConsumed % MISCONCEPTION_EVERY == MISCONCEPTION_OFFSET && misconceptionCursor < misconceptions.size) {
                out += FeedItem.Misconception(misconceptions[misconceptionCursor++])
            }
        }
        flushReviewQuizzesUpTo(newCards.size)

        _items.value = _items.value + out
    }

    // The server only counts a card as read once a second POST lands
    // >=4.5s after the first, by its own clock (MIN_READ_GAP_MS in
    // sparklet/src/app/api/interactions/route.ts) — dwellMs is informational,
    // never trusted. Scrolling away cancels this coroutine before the delay
    // completes; the entry POST already upserted the row so the card won't
    // repeat, it just earns no XP, same as a fast swipe server-side.
    //
    // Callers MUST only invoke this for the single card actually settled on
    // screen (see FeedScreen's pagerState.settledPage tracking) — firing it
    // for every composed page fabricates reads for cards nobody looked at,
    // since the 4.5s gap elapses regardless of whether the user was looking.
    // Only meaningful for FeedItem.Card pages — quiz/guess/misconception/
    // review-quiz pages get their XP from their own answer endpoint, not
    // this read-tracking flow.
    suspend fun trackView(cardId: String): XpSummary? {
        return try {
            api.postInteraction(cardId = cardId, token = authSession.token.value)
            delay(4_700)
            val response = api.postInteraction(cardId = cardId, dwellMs = 5_000, token = authSession.token.value)
            response.xp
        } catch (e: CancellationException) {
            // Expected on scroll-away — but a CancellationException must
            // propagate for structured concurrency to work correctly, unlike
            // a normal exception it can't just be swallowed into a null.
            throw e
        } catch (e: Exception) {
            null // Best-effort: a missed read ping costs this card's XP, nothing else.
        }
    }

    private companion object {
        const val QUIZ_EVERY = 10
        const val GUESS_EVERY = 12
        const val GUESS_OFFSET = 1
        const val MISCONCEPTION_EVERY = 10
        const val MISCONCEPTION_OFFSET = 2
    }
}
