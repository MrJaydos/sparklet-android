package com.sparklet.android.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.ReportReason
import com.sparklet.android.network.CardActionsApi
import com.sparklet.android.ui.theme.SparkletColors
import kotlinx.coroutines.launch

// Mirrors web's ReportSheet.tsx. The backend treats a repeat report from the
// same user as a success with alreadyReported = true rather than an error,
// so that case gets its own acknowledgement instead of looking like a
// duplicate submission failed.
@Composable
fun ReportSheet(cardId: String, token: String?, onDone: () -> Unit) {
    val api = remember { CardActionsApi() }
    val scope = rememberCoroutineScope()
    var reason by remember(cardId) { mutableStateOf<ReportReason?>(null) }
    var detail by remember(cardId) { mutableStateOf("") }
    var result by remember(cardId) { mutableStateOf<String?>(null) }
    var isSending by remember(cardId) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Report this card",
            style = MaterialTheme.typography.titleLarge,
            color = SparkletColors.TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        val message = result
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = SparkletColors.TextSecondary)
            Text(
                "Done",
                style = MaterialTheme.typography.labelLarge,
                color = SparkletColors.AccentText,
                modifier = Modifier.clickable(onClick = onDone).padding(top = 16.dp, bottom = 8.dp),
            )
            return@Column
        }

        for (option in ReportReason.entries) {
            val selected = option == reason
            Text(
                option.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) SparkletColors.TextPrimary else SparkletColors.TextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (selected) Modifier.background(SparkletColors.Accent.copy(alpha = 0.18f))
                        else Modifier.border(1.dp, SparkletColors.Border, RoundedCornerShape(12.dp))
                    )
                    .clickable { reason = option }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        OutlinedTextField(
            value = detail,
            onValueChange = { detail = it },
            placeholder = { Text("Anything else? (optional)", color = SparkletColors.TextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = SparkletColors.TextPrimary,
                unfocusedTextColor = SparkletColors.TextPrimary,
                focusedBorderColor = SparkletColors.Accent,
                unfocusedBorderColor = SparkletColors.Border,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        val chosen = reason
        Text(
            if (isSending) "Sending…" else "Submit report",
            style = MaterialTheme.typography.labelLarge,
            color = if (chosen == null) SparkletColors.TextMuted else SparkletColors.AccentText,
            modifier = Modifier
                .padding(top = 16.dp)
                .clickable(enabled = chosen != null && !isSending) {
                    isSending = true
                    scope.launch {
                        result = try {
                            val response = api.reportCard(
                                cardId = cardId,
                                reason = chosen!!,
                                detail = detail.trim().ifEmpty { null },
                                token = token,
                            )
                            if (response.alreadyReported == true) {
                                "You've already reported this one — it's with the moderators."
                            } else {
                                "Thanks — a moderator will take a look."
                            }
                        } catch (e: Exception) {
                            "Couldn't send that report. Try again in a moment."
                        } finally {
                            isSending = false
                        }
                    }
                }
                .padding(vertical = 8.dp),
        )
    }
}
