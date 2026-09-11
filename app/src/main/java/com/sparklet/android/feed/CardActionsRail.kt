package com.sparklet.android.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.FeedCard
import com.sparklet.android.network.CardActionsApi
import com.sparklet.android.ui.theme.SparkletColors
import kotlinx.coroutines.launch

// Vote / save / comment / report, mirroring the web's card action rail.
//
// State is seeded from the card and then owned locally, because the feed
// batch that produced this FeedCard is never re-fetched after an action —
// re-reading /api/feed to reflect a vote would reshuffle the user's place in
// the feed. The server response is authoritative for the new values, so each
// action adopts what it returns rather than incrementing locally.
@Composable
fun CardActionsRail(
    card: FeedCard,
    token: String?,
    onOpenComments: () -> Unit,
    onOpenReport: () -> Unit,
) {
    val api = remember { CardActionsApi() }
    val scope = rememberCoroutineScope()

    var score by remember(card.id) { mutableIntStateOf(card.score) }
    var myVote by remember(card.id) { mutableIntStateOf(card.myVote) }
    var saved by remember(card.id) { mutableStateOf(card.saved) }

    fun vote(value: Int) {
        scope.launch {
            // Tapping the active arrow clears the vote, same as the web.
            val next = if (myVote == value) 0 else value
            try {
                val response = api.vote(card.id, next, token)
                score = response.score
                myVote = response.myVote
            } catch (e: Exception) {
                // Leave the previous state showing rather than a value the
                // server didn't confirm.
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RailAction("⬆", if (myVote > 0) SparkletColors.AccentText else SparkletColors.TextMuted) {
            vote(1)
        }
        Text(
            "$score",
            style = MaterialTheme.typography.labelLarge,
            color = SparkletColors.TextSecondary,
        )
        RailAction("⬇", if (myVote < 0) SparkletColors.DangerText else SparkletColors.TextMuted) {
            vote(-1)
        }

        RailAction(
            if (saved) "🔖" else "🏷️",
            if (saved) SparkletColors.AccentText else SparkletColors.TextMuted,
        ) {
            scope.launch {
                try {
                    saved = api.setSaved(card.id, !saved, token).saved
                } catch (e: Exception) {
                    // Same reasoning as vote().
                }
            }
        }

        RailAction("💬 ${card.commentCount}", SparkletColors.TextMuted, onClick = onOpenComments)
        RailAction("🚩", SparkletColors.TextMuted, onClick = onOpenReport)
    }
}

@Composable
private fun RailAction(label: String, color: Color, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp),
    )
}
