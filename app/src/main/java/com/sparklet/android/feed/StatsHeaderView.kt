package com.sparklet.android.feed

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.ProfileResponse

// Kept visually and logically separate, per AGENTS.md: the streak/XP row
// answers "did I hit my XP today", the (unused here yet) card-count goal
// answers a different question and should never be merged into this row.
@Composable
fun StatsHeaderView(profile: ProfileResponse?) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (profile != null) {
            Text("🔥 ${profile.currentStreak}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(16.dp))
            Text("⭐ ${profile.xpToday}/${profile.xpGoal} XP", style = MaterialTheme.typography.titleMedium)
        } else {
            CircularProgressIndicator(modifier = Modifier.width(20.dp))
        }
    }
}
