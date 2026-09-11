package com.sparklet.android.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sparklet.android.ui.theme.SparkletColors

private val MEDALS = listOf("🥇", "🥈", "🥉")

// Ports sparklet-ios's LeaderboardView. Rank is the row's position in the
// list rather than anything the API sends — the route returns `rows` already
// ordered, and only the viewer's own out-of-top rank comes back explicitly
// (as `me.rank`), which is why that one row is rendered separately below.
@Composable
fun LeaderboardScreen(viewModel: LeaderboardViewModel) {
    val response by viewModel.response.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val board by viewModel.board.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "🏆 Leaderboard",
            style = MaterialTheme.typography.titleLarge,
            color = SparkletColors.TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in LeaderboardViewModel.Board.entries) {
                BoardChip(
                    label = option.label,
                    selected = option == board,
                    onClick = { viewModel.selectBoard(option) },
                )
            }
        }

        val current = response
        when {
            current == null && isLoading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SparkletColors.TextTertiary)
            }

            current == null -> Text(
                "Couldn't load the leaderboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (current.rows.isEmpty()) {
                    item {
                        Text(
                            emptyMessage(board),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SparkletColors.TextTertiary,
                        )
                    }
                } else {
                    itemsIndexed(current.rows, key = { _, row -> row.userId }) { index, row ->
                        RankRow(
                            rank = index + 1,
                            name = row.name,
                            xp = row.xp,
                            isSelf = row.userId == current.viewerId,
                        )
                    }
                }

                // Pinned below the list so the viewer can always see where
                // they stand, even when they're nowhere near the top.
                val me = current.me
                if (me != null && !current.inTop) {
                    item {
                        RankRow(
                            rank = me.rank,
                            name = current.selfName,
                            xp = me.xp,
                            isSelf = true,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = if (selected) Color.White else SparkletColors.TextTertiary,
        modifier = Modifier
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(SparkletColors.Accent)
                else Modifier.border(1.dp, SparkletColors.Border, CircleShape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun RankRow(
    rank: Int,
    name: String,
    xp: Int,
    isSelf: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelf) SparkletColors.Accent.copy(alpha = 0.12f) else SparkletColors.Panel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (rank <= MEDALS.size) MEDALS[rank - 1] else "$rank",
            style = MaterialTheme.typography.bodyMedium,
            color = SparkletColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = SparkletColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isSelf) {
            Text("you", style = MaterialTheme.typography.labelSmall, color = SparkletColors.AccentText)
        }
        Box(modifier = Modifier.weight(1f))
        Text(
            "⚡ $xp",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = SparkletColors.AccentText,
        )
    }
}

private fun emptyMessage(board: LeaderboardViewModel.Board): String = when (board) {
    LeaderboardViewModel.Board.FRIENDS ->
        "No friends on the board yet — add some from the friends screen."
    LeaderboardViewModel.Board.TODAY ->
        "Nobody has earned XP today yet — the first card you read puts you on the board."
    LeaderboardViewModel.Board.WEEK ->
        "Nobody has earned XP this week yet — the first card you read puts you on the board."
    LeaderboardViewModel.Board.ALL ->
        "Nobody has earned XP yet — the first card you read puts you on the board."
}
