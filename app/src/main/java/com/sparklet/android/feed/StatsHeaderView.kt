package com.sparklet.android.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.ProfileResponse
import com.sparklet.android.ui.theme.SparkletColors

// Kept visually and logically separate, per AGENTS.md: the streak/XP row
// answers "did I hit my XP today", the (unused here yet) card-count goal
// answers a different question and should never be merged into this row.
//
// Also the entry point to every screen that isn't the feed — sparklet-ios
// hangs the same set off its own header rather than using a tab bar, since
// the feed is the app and everything else is a detour from it.
@Composable
fun StatsHeaderView(
    profile: ProfileResponse?,
    onOpenLeaderboard: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (profile != null) {
            Text(
                "🔥 ${profile.currentStreak}",
                style = MaterialTheme.typography.titleMedium,
                color = SparkletColors.TextPrimary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                "⭐ ${profile.xpToday}/${profile.xpGoal} XP",
                style = MaterialTheme.typography.titleMedium,
                color = SparkletColors.TextPrimary,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.width(20.dp),
                color = SparkletColors.TextTertiary,
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            "🏆",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier
                .clickable(onClick = onOpenLeaderboard)
                .padding(8.dp),
        )
    }
}
