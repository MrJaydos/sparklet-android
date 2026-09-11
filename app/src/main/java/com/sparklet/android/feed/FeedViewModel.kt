package com.sparklet.android.feed

import androidx.lifecycle.ViewModel
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.FeedCard
import com.sparklet.android.model.FeedExplainPrompt
import com.sparklet.android.model.FeedGuess
import com.sparklet.android.model.FeedItem
import com.sparklet.android.model.FeedMisconception
import com.sparklet.android.model.FeedQuiz
import com.sparklet.android.model.FeedResponse
import com.sparklet.android.model.FeedReviewQuiz
import com.sparklet.android.model.XpSummary
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.OnboardingApi
import com.sparklet.android.network.FeedApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FeedViewModel(
    private val authSession: AuthSession,
    private val preferences: FeedPreferences,
) : ViewModel() {
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
    private var explainPrompts = emptyList<FeedExplainPrompt>()
    private var explainCursor = 0

    // Running count of FeedCards folded into `items` so far, across the
    // whole session. The _EVERY pacing constants below are defined against
    // this count, not items.size — items.size also counts the inserted
    // challenge entries, so it outpaces the card stream the constants were
    // tuned for.
    private var cardsConsumed = 0

    // Once true, every later pagination call asks the server for repeats: the
    // account has genuinely seen every unseen/due card this session, and this
    // is a scroll-for-hours feed, not a fixed deck that dead-ends. Mirrors the
    // web's own `exhausted` flag, except the web stops and offers a "You're
    // all caught up" button to opt into repeats manually — both native
    // clients opt in automatically, since an endless feed is the product
    // shape here. Without this the feed silently stopped appending after
    // roughly one lap through the pool.
    private var exhausted = false

    // Session-recap state — purely client-side and scoped to this app launch,
    // mirroring Feed.tsx's sessionViewsRef/sessionCategories.
    private val _sessionViews = MutableStateFlow(0)
    val sessionViews: StateFlow<Int> = _sessionViews.asStateFlow()
    private val _sessionTopicCount = MutableStateFlow(0)
    val sessionTopicCount: StateFlow<Int> = _sessionTopicCount.asStateFlow()
    private val viewedCardIds = mutableSetOf<String>()
    private val sessionCategories = mutableSetOf<String>()

    // Resolved once per view model lifetime, i.e. effectively once per app
    // launch, since the feed only ever creates one.
    private val showInviteCard = preferences.nextSessionShowsInvite()
    private var inviteShown = false
    private var goalReached = false

    private val onboardingApi = OnboardingApi()

    // The topic filter. The UserInterest table is the durable cross-device
    // source of truth (see OnboardingApi.fetchInterests), so this is read
    // from the server on first load rather than persisted locally. Empty
    // means "everything", which is also what the backend treats an empty
    // selection as.
    private val _categorySlugs = MutableStateFlow<List<String>>(emptyList())
    val categorySlugs: StateFlow<List<String>> = _categorySlugs.asStateFlow()
    private var interestsLoaded = false

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    // Persists the topic filter server-side, then reloads the feed outright:
    // the already-loaded batch was composed under the old filter, so keeping
    // it would leave the user looking at topics they just deselected.
    suspend fun setInterests(slugs: List<String>) {
        _categorySlugs.value = slugs
        runCatching { onboardingApi.submitInterests(slugs, authSession.token.value) }
        load()
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
            // Fetched once per session, before the first batch, so the very
            // first cards already respect the filter rather than showing an
            // unfiltered batch that then changes under the user.
            if (!interestsLoaded) {
                interestsLoaded = true
                _categorySlugs.value = runCatching {
                    onboardingApi.fetchInterests(authSession.token.value)
                }.getOrDefault(emptyList())
            }
            var response = api.fetchFeed(
                categorySlugs = _categorySlugs.value,
                take = 10,
                token = authSession.token.value,
            )
            if (response.exhausted && isEmptyBatch(response)) {
                // A returning account that has seen every card and has no due
                // reviews — fall straight back to repeats rather than opening
                // on an empty feed.
                response = api.fetchFeed(
                    categorySlugs = _categorySlugs.value,
                    take = 10,
                    allowRepeats = true,
                    token = authSession.token.value,
                )
            }
            exhausted = response.exhausted
            cards = response.cards
            quizzes = response.quizzes
            quizCursor = 0
            guesses = response.guesses
            guessCursor = 0
            misconceptions = response.misconceptions
            misconceptionCursor = 0
            explainPrompts = response.explainPrompts
            explainCursor = 0
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
            val excludeIds = recentExcludeIds()
            var response = api.fetchFeed(
                categorySlugs = _categorySlugs.value,
                take = 10,
                allowRepeats = exhausted,
                excludeIds = excludeIds,
                token = authSession.token.value,
            )
            if (response.exhausted && isEmptyBatch(response) && !exhausted) {
                // Just crossed into "seen everything new" for the first time
                // this session; the call above already tried without repeats.
                // Retry once immediately so this pagination trigger still
                // makes forward progress — the trigger only fires again when
                // the user scrolls past the newly-loaded tail, which can never
                // happen if nothing was appended.
                response = api.fetchFeed(
                    categorySlugs = _categorySlugs.value,
                    take = 10,
                    allowRepeats = true,
                    excludeIds = excludeIds,
                    token = authSession.token.value,
                )
            }
            exhausted = response.exhausted
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
            val knownExplainIds = explainPrompts.map { it.id }.toSet()
            explainPrompts = explainPrompts + response.explainPrompts.filter { it.id !in knownExplainIds }
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

    // Counts a card toward the session recap the moment it's scrolled to,
    // deliberately independent of the 4.5s server-clock read gate: "I scrolled
    // past this" and "the server counted it as read" are different questions,
    // and the recap copy is about the former. Keyed by cardId rather than
    // occurrence so a recirculated repeat doesn't inflate the count twice.
    fun markSessionView(card: FeedCard) {
        if (!viewedCardIds.add(card.id)) return
        _sessionViews.value = viewedCardIds.size
        addSessionCategory(card.category.name)
    }

    // Also called after a challenge answer: those count toward the session's
    // topic tally even though they aren't cards.
    fun addSessionCategory(name: String?) {
        if (name == null) return
        sessionCategories.add(name)
        _sessionTopicCount.value = sessionCategories.size
    }

    // Appends the goal-reached slide the first time the daily card-count goal
    // is crossed this session. iOS snapshots a position and rebuilds the whole
    // item list; this client builds items incrementally by appending, so the
    // slide is appended at the current tail instead — the user meets it on the
    // next swipe either way, and rebuilding would disturb the pager underneath
    // them. FeedPreferences' own date guard keeps it to once per local day.
    fun markGoalReachedIfNeeded(cardsToday: Int) {
        if (goalReached) return
        if (cardsToday < preferences.dailyCardGoal) return
        if (!preferences.markGoalReachedIfNeededToday()) {
            // Already celebrated today in an earlier session — don't show it
            // again, but don't keep re-checking either.
            goalReached = true
            return
        }
        goalReached = true
        _items.value = _items.value + FeedItem.GoalReached
    }

    // Sent as `exclude` so the server doesn't resurface something already on
    // screen. Deliberately a bounded recent window rather than the full
    // accumulated history: once `exhausted` flips true and the server starts
    // returning previously-seen cards, an ever-growing exclude list would
    // also permanently exclude every repeat candidate after one lap — turning
    // "endless scroll" into "ends after two laps instead of one". It also
    // kept growing the query string without limit. Review-quiz source cards
    // are included because a skipped, unanswered review-quiz would otherwise
    // reappear immediately, its due state never having changed.
    private fun recentExcludeIds(): List<String> =
        cards.takeLast(EXCLUDE_WINDOW).map { it.id } +
            _items.value.filterIsInstance<FeedItem.ReviewQuiz>().map { it.quiz.sourceCardId }

    // "The server had nothing left to give": used with `exhausted` to decide
    // whether to immediately retry allowing repeats.
    private fun isEmptyBatch(response: FeedResponse): Boolean =
        response.cards.isEmpty() &&
            response.quizzes.isEmpty() &&
            response.reviewQuizzes.isEmpty() &&
            response.guesses.isEmpty() &&
            response.misconceptions.isEmpty() &&
            response.explainPrompts.isEmpty()

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
        // Index of this batch's first card within the cumulative pool; `cards`
        // is always assigned before this runs. See FeedItem.Card.occurrence.
        val occurrenceBase = cards.size - newCards.size
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
            out += FeedItem.Card(card, occurrence = occurrenceBase + i)
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
            if (cardsConsumed % EXPLAIN_EVERY == EXPLAIN_OFFSET && explainCursor < explainPrompts.size) {
                out += FeedItem.Explain(explainPrompts[explainCursor++])
            }
            if (cardsConsumed % CHECKIN_EVERY == 0) {
                out += FeedItem.Checkin(afterCount = cardsConsumed)
            }
            if (showInviteCard && !inviteShown && cardsConsumed == INVITE_AFTER_CARDS) {
                inviteShown = true
                out += FeedItem.Invite
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
        const val EXPLAIN_EVERY = 12
        const val EXPLAIN_OFFSET = 3
        const val EXCLUDE_WINDOW = 60
        const val CHECKIN_EVERY = 15
        const val INVITE_AFTER_CARDS = 12
    }
}
