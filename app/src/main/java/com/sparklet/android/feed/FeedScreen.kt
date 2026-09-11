package com.sparklet.android.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparklet.android.auth.AuthSession
import com.sparklet.android.model.FeedItem
import com.sparklet.android.leaderboard.LeaderboardScreen
import com.sparklet.android.leaderboard.LeaderboardViewModel
import com.sparklet.android.friends.FriendsScreen
import com.sparklet.android.friends.FriendsViewModel
import com.sparklet.android.map.KnowledgeMapScreen
import com.sparklet.android.map.KnowledgeMapViewModel
import com.sparklet.android.model.pagerKey
import com.sparklet.android.onboarding.OnboardingScreen
import com.sparklet.android.onboarding.OnboardingViewModel
import com.sparklet.android.notifications.NotificationsScreen
import com.sparklet.android.notifications.NotificationsViewModel
import com.sparklet.android.profile.ProfileScreen
import com.sparklet.android.profile.ProfileViewModel
import com.sparklet.android.ui.theme.SparkletColors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
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
    val context = LocalContext.current
    val preferences = remember { FeedPreferences(context) }
    val viewModel = viewModel { FeedViewModel(authSession, preferences) }
    val statsViewModel = viewModel { StatsHeaderViewModel(authSession) }

    val items by viewModel.items.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val profile by statsViewModel.profile.collectAsState()
    val token by authSession.token.collectAsState()
    val sessionViews by viewModel.sessionViews.collectAsState()
    val sessionTopicCount by viewModel.sessionTopicCount.collectAsState()

    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showingLeaderboard by remember { mutableStateOf(false) }
    var showingNotifications by remember { mutableStateOf(false) }
    var showingProfile by remember { mutableStateOf(false) }
    var showingFriends by remember { mutableStateOf(false) }
    var showingMap by remember { mutableStateOf(false) }
    var commentsCardId by remember { mutableStateOf<String?>(null) }
    var reportCardId by remember { mutableStateOf<String?>(null) }
    var detailCardId by remember { mutableStateOf<String?>(null) }
    var showingSettings by remember { mutableStateOf(false) }
    val categorySlugs by viewModel.categorySlugs.collectAsState()
    // Edited locally while the sheet is open and committed on dismiss —
    // applying per tap would POST /api/interests and rebuild the feed once
    // per chip.
    var pendingTopics by remember { mutableStateOf<Set<String>?>(null) }
    // Server-computed one-time condition. Latched into local state once seen
    // so that dismissing it sticks for the session — the profile response it
    // came from isn't re-fetched, and re-showing it after "Skip" would be
    // worse than showing it once too rarely.
    var showingOnboarding by remember { mutableStateOf(false) }
    // Gated on more than one item, like the web's: a hint telling you to
    // swipe is worse than useless over a feed with nothing to swipe to.
    var showingSwipeHint by remember { mutableStateOf(!preferences.hasSeenSwipeHint) }
    var onboardingHandled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadIfNeeded()
        statsViewModel.load()
    }

    // Tracks the settled *item*, not the settled page index. Watching the
    // index alone silently lost the first card of every session: at first
    // composition `items` is still empty, so settledPage emits 0, getOrNull(0)
    // returns null, and distinctUntilChanged() then swallows the page-0
    // re-emission once the batch actually arrives — page 0 was never tracked
    // and card 1 earned nothing, every session.
    //
    // Combining with viewModel.items fixes that (the list arriving is itself
    // an emission) while distinctUntilChangedBy { pagerKey } preserves what
    // the index-based version was protecting against: a pagination append
    // re-emits the list, but the settled item is unchanged, so it does not
    // fire a second redundant interaction POST pair for the card the user is
    // already sitting on. Keyed on Unit so the collector is never torn down
    // and recreated mid-session.
    LaunchedEffect(Unit) {
        combine(
            snapshotFlow { pagerState.settledPage },
            viewModel.items,
        ) { page, list -> list.getOrNull(page) }
            .filterNotNull()
            .distinctUntilChangedBy { it.pagerKey }
            .collect { item ->
                if (item is FeedItem.Card) {
                    // Counted on arrival, before the dwell gate — see
                    // markSessionView. trackView suspends ~4.7s below, so
                    // doing this after would delay the recap copy by a card.
                    viewModel.markSessionView(item.card)
                    val xp = viewModel.trackView(item.card.id)
                    if (xp != null) {
                        statsViewModel.apply(xp)
                        viewModel.markGoalReachedIfNeeded(
                            statsViewModel.profile.value?.cardsToday ?: 0
                        )
                    }
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
                // Any settle past the first page means the user has worked
                // out the gesture — retire the hint for good.
                if (page > 0 && showingSwipeHint) {
                    showingSwipeHint = false
                    preferences.hasSeenSwipeHint = true
                }
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

    LaunchedEffect(profile?.needsOnboarding) {
        if (profile?.needsOnboarding == true && !onboardingHandled) {
            onboardingHandled = true
            showingOnboarding = true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatsHeaderView(
            profile = profile,
            onOpenLeaderboard = { showingLeaderboard = true },
            onOpenNotifications = { showingNotifications = true },
            onOpenProfile = { showingProfile = true },
            onOpenFriends = { showingFriends = true },
            onOpenMap = { showingMap = true },
            onOpenSettings = {
                pendingTopics = categorySlugs.toSet()
                showingSettings = true
            },
        )

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
                        is FeedItem.Card -> CardView(
                            card = item.card,
                            token = token,
                            onOpenComments = { commentsCardId = item.card.id },
                            onOpenReport = { reportCardId = item.card.id },
                            onOpenRelated = { detailCardId = it },
                        )
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
                        is FeedItem.Checkin -> CheckinSlide(
                            sessionViews = sessionViews,
                            topicCount = sessionTopicCount,
                            onContinue = { advance(page) },
                        )
                        FeedItem.Invite -> InviteSlide(onContinue = { advance(page) })
                        FeedItem.GoalReached -> GoalReachedSlide(
                            cardsToday = profile?.cardsToday ?: 0,
                            dailyGoal = preferences.dailyCardGoal,
                            sessionViews = sessionViews,
                            topicCount = sessionTopicCount,
                            onContinue = { advance(page) },
                        )
                        is FeedItem.Explain -> ExplainAnswerView(
                            prompt = item.prompt,
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

                if (showingSwipeHint && items.size > 1) {
                    Text(
                        "Swipe up for the next card",
                        style = MaterialTheme.typography.labelLarge,
                        color = SparkletColors.TextTertiary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp),
                    )
                }
            }
        }
    }

    // Everything that isn't the feed opens as a sheet over it, matching
    // sparklet-ios — the feed is never torn down and its pager position,
    // in-flight read tracking and loaded batch all survive the detour.
    if (showingLeaderboard) {
        val leaderboardViewModel = viewModel { LeaderboardViewModel(authSession) }
        ModalBottomSheet(
            onDismissRequest = { showingLeaderboard = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            LeaderboardScreen(leaderboardViewModel)
        }
    }

    if (showingNotifications) {
        val notificationsViewModel = viewModel { NotificationsViewModel(authSession) }
        ModalBottomSheet(
            onDismissRequest = { showingNotifications = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            NotificationsScreen(notificationsViewModel)
        }
    }

    if (showingProfile) {
        val profileViewModel = viewModel { ProfileViewModel(authSession) }
        ModalBottomSheet(
            onDismissRequest = { showingProfile = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            ProfileScreen(profileViewModel)
        }
    }

    if (showingFriends) {
        val friendsViewModel = viewModel { FriendsViewModel(authSession) }
        ModalBottomSheet(
            onDismissRequest = { showingFriends = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            FriendsScreen(friendsViewModel)
        }
    }

    if (showingMap) {
        val mapViewModel = viewModel { KnowledgeMapViewModel(authSession) }
        ModalBottomSheet(
            onDismissRequest = { showingMap = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            KnowledgeMapScreen(mapViewModel)
        }
    }

    // Full-screen rather than a bottom sheet: it stands in for the web's own
    // /onboarding route, and a dismissible sheet would let the user swipe past
    // the one moment the topic picker is actually in front of them.
    if (showingOnboarding) {
        val onboardingViewModel = viewModel { OnboardingViewModel(authSession) }
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = SparkletColors.Background) {
                OnboardingScreen(
                    viewModel = onboardingViewModel,
                    onComplete = {
                        showingOnboarding = false
                        // The picks just written are the feed's topic filter,
                        // so pull them through rather than leaving the feed on
                        // the batch fetched before onboarding.
                        scope.launch { viewModel.load() }
                    },
                )
            }
        }
    }

    if (showingSettings) {
        ModalBottomSheet(
            onDismissRequest = {
                showingSettings = false
                val chosen = pendingTopics
                pendingTopics = null
                if (chosen != null && chosen != categorySlugs.toSet()) {
                    scope.launch { viewModel.setInterests(chosen.toList()) }
                }
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            FeedSettingsSheet(
                token = token,
                selected = pendingTopics ?: categorySlugs.toSet(),
                onSelectedChange = { pendingTopics = it },
            )
        }
    }

    detailCardId?.let { cardId ->
        ModalBottomSheet(
            onDismissRequest = { detailCardId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            CardDetailSheet(
                cardId = cardId,
                token = token,
                onOpenRelated = { detailCardId = it },
            )
        }
    }

    commentsCardId?.let { cardId ->
        ModalBottomSheet(
            onDismissRequest = { commentsCardId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            CommentsSheet(cardId = cardId, token = token)
        }
    }

    reportCardId?.let { cardId ->
        ModalBottomSheet(
            onDismissRequest = { reportCardId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SparkletColors.Background,
        ) {
            ReportSheet(cardId = cardId, token = token, onDone = { reportCardId = null })
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
