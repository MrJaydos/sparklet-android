package com.sparklet.android.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.Category
import com.sparklet.android.model.DepthLevel
import com.sparklet.android.network.OnboardingApi
import com.sparklet.android.ui.theme.SparkletColors
import com.sparklet.android.ui.theme.categoryColor

// Topic filter, daily card goal and default depth — the web's CategorySheet
// plus the depth preference, which sparklet-ios groups into one
// FeedSettingsView.
//
// Topics are applied on dismiss rather than per tap: each change would
// otherwise POST /api/interests and reload the whole feed, so picking four
// topics would rebuild the feed four times.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeedSettingsSheet(
    token: String?,
    selected: Set<String>,
    onSelectedChange: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { FeedPreferences(context) }
    val api = remember { OnboardingApi() }

    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var goal by remember { mutableStateOf(preferences.dailyCardGoal) }
    var depth by remember { mutableStateOf(preferences.depth ?: DepthLevel.STANDARD) }

    LaunchedEffect(Unit) {
        categories = runCatching { api.fetchCategories(token) }.getOrDefault(emptyList())
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Feed settings",
            style = MaterialTheme.typography.titleLarge,
            color = SparkletColors.TextPrimary,
        )

        SectionLabel("Topics")
        Text(
            "Nothing selected shows you everything.",
            style = MaterialTheme.typography.bodySmall,
            color = SparkletColors.TextTertiary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (category in categories) {
                val isOn = category.slug in selected
                Chip(
                    label = "${category.icon} ${category.name}",
                    selected = isOn,
                    selectedColor = categoryColor(category.colorHex),
                ) {
                    onSelectedChange(
                        if (isOn) selected - category.slug else selected + category.slug
                    )
                }
            }
        }

        SectionLabel("Daily card goal")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (option in FeedPreferences.DAILY_CARD_GOAL_OPTIONS) {
                Chip(
                    label = "$option",
                    selected = option == goal,
                    selectedColor = SparkletColors.Accent,
                ) {
                    goal = option
                    preferences.dailyCardGoal = option
                }
            }
        }

        SectionLabel("Card depth")
        Text(
            "Applies to cards as you scroll to them.",
            style = MaterialTheme.typography.bodySmall,
            color = SparkletColors.TextTertiary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (level in DepthLevel.entries) {
                Chip(
                    label = level.label(),
                    selected = level == depth,
                    selectedColor = SparkletColors.Accent,
                ) {
                    depth = level
                    preferences.depth = level
                }
            }
        }
    }
}

private fun DepthLevel.label(): String = when (this) {
    DepthLevel.SIMPLE -> "Simple"
    DepthLevel.STANDARD -> "Standard"
    DepthLevel.DEEP -> "Deep"
    DepthLevel.EXTRA_DEEP -> "Extra deep"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = SparkletColors.TextPrimary,
        modifier = Modifier.padding(top = 20.dp),
    )
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) Color.White else SparkletColors.TextTertiary,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(selectedColor)
                else Modifier.border(1.dp, SparkletColors.Border, CircleShape)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
