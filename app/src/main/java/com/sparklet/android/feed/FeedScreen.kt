package com.sparklet.android.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.FeedItem
import com.sparklet.android.model.pagerKey
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// Paged, one-item-at-a-time scroll: pagerState.settledPage tracks whichever
// item is actually settled on screen, and a single LaunchedEffect at the
// container level runs the read-tracking flow for that item only. This is
// not cosmetic — a naive per-page LaunchedEffect fires for every composed
// page (the 2-3 on screen plus Compose's own prefetch), and the backend's
// 4.5s server-clock gate (see FeedViewModel.trackView) can't tell that
// apart from a real read: the gap genuinely elapses even if the user never
// looked at those cards. Only tracking the single settled page keeps the
// client honest about what it's claiming, not just about what dwellMs it
// sends. settledPage (not currentPage, which changes mid-drag before the
// pager stops) is what makes this correct.
//
// The pager renders a FeedItem, not a plain FeedCard — quiz/guess/
// misconception/review-quiz challenges are interleaved into the card
// stream client-side (see FeedViewModel.interleaveBatch) and get their own
// full-screen answer views. Read-tracking only applies to FeedItem.Card:
// challenge items earn XP from their own answer endpoint instead.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(authSession: AuthSession) {
    val viewModel = viewModel { FeedViewModel(authSession) }
    val statsViewModel = viewModel { StatsHeaderViewModel(authSession) }

    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val profile by statsViewModel.profile.collectAsState()
    val token by authSession.token.collectAsState()

    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
        statsViewModel.load()
    }

    // Keyed on Unit, not items.size: restarting this on every pagination
    // append would tear down and recreate the collector, and snapshotFlow
    // re-emits the *current* settledPage as soon as a new collector starts —
    // firing a second, redundant interaction POST pair for the item the user
    // is already sitting on every time a new batch loads in. Reading
    // viewModel.items.value fresh inside the collector (instead of closing
    // over the composable's `items` snapshot) keeps this correct without
    // needing the restart.
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                val item = viewModel.items.value.getOrNull(page) ?: return@collect
                if (item is FeedItem.Card) {
                    val xp = viewModel.trackView(item.card.id)
                    if (xp != null) statsViewModel.apply(xp)
                }
            }
    }

    // Deliberately a separate LaunchedEffect from the one above: trackView
    // suspends ~4.7s before its second POST, so pagination sitting behind it
    // in the same collector would never run on a swipe faster than that —
    // exactly the core interaction of a swipe feed. Both react to the same
    // settledPage changes independently.
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page ->
                viewModel.loadMoreIfNeeded(page, viewModel.items.value.size)
            }
    }

    // Advances to the next page once a challenge item's answer has been
    // revealed. Guards against `fromPage + 1` not existing yet — the next
    // batch may not have arrived even though loadMoreIfNeeded already
    // triggered near the tail — rather than crash the pager on an
    // out-of-range page index.
    fun advance(fromPage: Int) {
        scope.launch {
            val target = fromPage + 1
            if (target < viewModel.items.value.size) {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatsHeaderView(profile)

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.load()
                    // load() replaces the batch outright, so the old
                    // settled page (if it even still exists in the new
                    // batch) shouldn't carry over — reset to page 0 so
                    // tracking picks up on item 1 of the refreshed feed.
                    pagerState.scrollToPage(0)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    key = { index -> items.getOrNull(index)?.pagerKey ?: index },
                ) { page ->
                    when (val item = items[page]) {
                        is FeedItem.Card -> CardView(card = item.card)
                        is FeedItem.Quiz -> QuizAnswerView(
                            id = item.quiz.id,
                            question = item.quiz.question,
                            options = item.quiz.options,
                            category = item.quiz.category,
                            variant = QuizVariant.CHECKPOINT,
                            token = token,
                            onResult = { xp -> statsViewModel.apply(xp) },
                            onContinue = { advance(page) },
                        )
                        is FeedItem.ReviewQuiz -> QuizAnswerView(
                            id = item.quiz.id,
                            question = item.quiz.question,
                            options = item.quiz.options,
                            category = item.quiz.category,
                            variant = QuizVariant.REVIEW,
                            token = token,
                            onResult = { xp -> statsViewModel.apply(xp) },
                            onContinue = { advance(page) },
                        )
                        is FeedItem.Guess -> GuessAnswerView(
                            guess = item.guess,
                            token = token,
                            onResult = { xp -> statsViewModel.apply(xp) },
                            onContinue = { advance(page) },
                        )
                        is FeedItem.Misconception -> MisconceptionAnswerView(
                            misconception = item.misconception,
                            token = token,
                            onResult = { xp -> statsViewModel.apply(xp) },
                            onContinue = { advance(page) },
                        )
                    }
                }
                if (isLoading && items.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(message) },
        )
    }
}
