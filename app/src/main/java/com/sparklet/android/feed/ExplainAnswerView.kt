package com.sparklet.android.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.ExplainAnswerResponse
import com.sparklet.android.model.FeedExplainPrompt
import com.sparklet.android.model.XpSummary
import com.sparklet.android.network.ChallengeApi
import com.sparklet.android.ui.theme.SparkletColors
import com.sparklet.android.ui.theme.categoryColor
import kotlinx.coroutines.launch

// "Explain it back" — the one challenge kind the backend grades with prose
// rather than scoring against a fixed answer, so the response carries
// feedback text alongside XP. Mirrors web's ExplainCard and iOS's
// ExplainCardView, including the 10-600 character bounds the route enforces.
@Composable
fun ExplainAnswerView(
    prompt: FeedExplainPrompt,
    token: String?,
    onResult: (XpSummary) -> Unit,
    onContinue: () -> Unit,
) {
    val api = remember { ChallengeApi() }
    val scope = rememberCoroutineScope()
    var text by remember(prompt.id) { mutableStateOf("") }
    var result by remember(prompt.id) { mutableStateOf<ExplainAnswerResponse?>(null) }
    var wasSkipped by remember(prompt.id) { mutableStateOf(false) }
    var isSubmitting by remember(prompt.id) { mutableStateOf(false) }

    val canSubmit = text.length in 10..600

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "${prompt.category.icon} Explain it back",
            style = MaterialTheme.typography.labelMedium,
            color = categoryColor(prompt.category.colorHex),
        )
        Text(
            prompt.title,
            style = MaterialTheme.typography.headlineSmall,
            color = SparkletColors.TextPrimary,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
        )

        val current = result
        if (current == null) {
            Text(
                prompt.body,
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextSecondary,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                enabled = !isSubmitting,
                placeholder = {
                    Text("In your own words…", color = SparkletColors.TextMuted)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SparkletColors.TextPrimary,
                    unfocusedTextColor = SparkletColors.TextPrimary,
                    focusedBorderColor = SparkletColors.Accent,
                    unfocusedBorderColor = SparkletColors.Border,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(
                "${text.length}/600",
                style = MaterialTheme.typography.labelSmall,
                color = if (canSubmit || text.isEmpty()) SparkletColors.TextMuted
                else SparkletColors.DangerText,
                modifier = Modifier.padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    color = SparkletColors.TextTertiary,
                    modifier = Modifier
                        .clickable(enabled = !isSubmitting) {
                            isSubmitting = true
                            scope.launch {
                                // Skipping still posts: the route records it
                                // so the prompt isn't handed out again.
                                runCatching { api.skipExplain(prompt.id, token) }
                                    .onSuccess { response ->
                                        wasSkipped = true
                                        result = response
                                        onResult(response.xp)
                                    }
                                isSubmitting = false
                            }
                        }
                        .padding(8.dp),
                )
                Text(
                    if (isSubmitting) "…" else "Submit",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSubmit) SparkletColors.AccentText else SparkletColors.TextMuted,
                    modifier = Modifier
                        .clickable(enabled = canSubmit && !isSubmitting) {
                            isSubmitting = true
                            scope.launch {
                                runCatching { api.answerExplain(prompt.id, text, token) }
                                    .onSuccess { response ->
                                        result = response
                                        onResult(response.xp)
                                    }
                                isSubmitting = false
                            }
                        }
                        .padding(8.dp),
                )
            }
        } else {
            Text(
                if (wasSkipped) "Skipped" else scoreHeadline(current.score),
                style = MaterialTheme.typography.titleMedium,
                color = if (wasSkipped) SparkletColors.TextTertiary
                else if (current.score >= 0.7) SparkletColors.SuccessText
                else SparkletColors.DangerText,
            )
            if (!wasSkipped) {
                Text(
                    current.feedback,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SparkletColors.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "Continue",
                style = MaterialTheme.typography.labelLarge,
                color = SparkletColors.AccentText,
                modifier = Modifier.padding(top = 20.dp).clickable(onClick = onContinue).padding(8.dp),
            )
        }
    }
}

// Matches the web's 0.7 pass threshold, the same cut iOS uses to pick its
// success haptic.
private fun scoreHeadline(score: Double): String =
    if (score >= 0.7) "🎯 That's it" else "🤔 Not quite"
