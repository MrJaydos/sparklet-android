package com.sparklet.android.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sparklet.android.ui.theme.SparkletColors

// Session-recap and growth slides. No server model — these are inserted into
// the stream client-side, mirroring Feed.tsx.

@Composable
fun CheckinSlide(sessionViews: Int, topicCount: Int, onContinue: () -> Unit) {
    RecapSlide(
        emoji = "✨",
        title = "You've learned $sessionViews ${plural(sessionViews, "thing")} " +
            "across $topicCount ${plural(topicCount, "topic")}",
        body = "Keep going, or come back later — your streak is safe for today.",
        actionLabel = "Keep going",
        onAction = onContinue,
    )
}

@Composable
fun InviteSlide(onContinue: () -> Unit) {
    RecapSlide(
        emoji = "🧊",
        title = "Know someone who'd love this?",
        body = "Invite a friend to Sparklet — you'll earn a bonus streak freeze when they join.",
        actionLabel = "Keep going",
        onAction = onContinue,
    )
}

@Composable
fun GoalReachedSlide(
    cardsToday: Int,
    dailyGoal: Int,
    sessionViews: Int,
    topicCount: Int,
    onContinue: () -> Unit,
) {
    RecapSlide(
        emoji = "🎉",
        title = "Daily goal complete!",
        body = "$cardsToday cards today — you hit your goal of $dailyGoal. " +
            "That's $sessionViews this session across $topicCount " +
            "${plural(topicCount, "topic")}. Ending on purpose beats endless scrolling.",
        actionLabel = "Keep going",
        onAction = onContinue,
    )
}

@Composable
private fun RecapSlide(
    emoji: String,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.displaySmall)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = SparkletColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = SparkletColors.TextTertiary,
            textAlign = TextAlign.Center,
        )
        Text(
            actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = SparkletColors.AccentText,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onAction).padding(12.dp),
        )
    }
}

private fun plural(count: Int, word: String): String = if (count == 1) word else "${word}s"
