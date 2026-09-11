package com.sparklet.android.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sparklet.android.ui.theme.SparkletColors
import com.sparklet.android.ui.theme.categoryColor

// Stands in for the web's separate /onboarding route — this client has no
// server-driven page redirect to hook into, so the feed presents it when
// ProfileResponse.needsOnboarding comes back true.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onComplete: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    val selected by viewModel.selectedSlugs.collectAsState()
    val name by viewModel.name.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadCategories() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "Welcome to Sparklet",
            style = MaterialTheme.typography.headlineSmall,
            color = SparkletColors.TextPrimary,
        )
        Text(
            "Learn something real, one swipe at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = SparkletColors.TextTertiary,
            modifier = Modifier.padding(top = 4.dp),
        )

        Text(
            "What should we call you?",
            style = MaterialTheme.typography.titleMedium,
            color = SparkletColors.TextPrimary,
            modifier = Modifier.padding(top = 28.dp),
        )
        OutlinedTextField(
            value = name,
            onValueChange = viewModel::setName,
            singleLine = true,
            placeholder = { Text("Your name (optional)", color = SparkletColors.TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SparkletColors.TextPrimary,
                unfocusedTextColor = SparkletColors.TextPrimary,
                focusedBorderColor = SparkletColors.Accent,
                unfocusedBorderColor = SparkletColors.Border,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        Text(
            "Pick a few topics",
            style = MaterialTheme.typography.titleMedium,
            color = SparkletColors.TextPrimary,
            modifier = Modifier.padding(top = 28.dp),
        )
        Text(
            "You can change these any time from feed settings.",
            style = MaterialTheme.typography.bodySmall,
            color = SparkletColors.TextTertiary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (category in categories) {
                val isOn = category.slug in selected
                Text(
                    "${category.icon} ${category.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isOn) Color.White else SparkletColors.TextTertiary,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clip(CircleShape)
                        .then(
                            if (isOn) Modifier.background(categoryColor(category.colorHex))
                            else Modifier.border(1.dp, SparkletColors.Border, CircleShape)
                        )
                        .clickable { viewModel.toggle(category.slug) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Skip",
                style = MaterialTheme.typography.labelLarge,
                color = SparkletColors.TextTertiary,
                modifier = Modifier
                    .clickable(enabled = !isSubmitting) {
                        viewModel.complete(skip = true, onDone = onComplete)
                    }
                    .padding(12.dp),
            )
            Text(
                if (isSubmitting) "…" else "Start learning",
                style = MaterialTheme.typography.labelLarge,
                color = SparkletColors.AccentText,
                modifier = Modifier
                    .clickable(enabled = !isSubmitting) {
                        viewModel.complete(skip = false, onDone = onComplete)
                    }
                    .padding(12.dp),
            )
        }
    }
}
