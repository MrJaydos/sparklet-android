package com.sparklet.android.invite

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.InviteStatus
import com.sparklet.android.ui.theme.SparkletColors

@Composable
fun InviteScreen(viewModel: InviteViewModel, refId: String, onContinue: () -> Unit) {
    val response by viewModel.response.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val failed by viewModel.failed.collectAsState()

    LaunchedEffect(refId) { viewModel.accept(refId) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val current = response
        when {
            isLoading && current == null ->
                CircularProgressIndicator(color = SparkletColors.TextTertiary)

            current != null -> {
                val name = current.referrerName ?: "Your friend"
                Text(
                    when (current.status) {
                        InviteStatus.FRIENDED -> "🎉 You and $name are now friends"
                        InviteStatus.ALREADY -> "You and $name are already friends"
                        InviteStatus.SELF -> "That's your own invite link"
                        InviteStatus.INVALID -> "That invite link isn't valid"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = SparkletColors.TextPrimary,
                )
                if (current.rewardGranted) {
                    Text(
                        "❄️ You both earned a streak freeze",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SparkletColors.AccentText,
                    )
                }
            }

            failed -> Text(
                "Couldn't open that invite.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
            )
        }

        Text(
            "Continue",
            style = MaterialTheme.typography.labelLarge,
            color = SparkletColors.AccentText,
            modifier = Modifier.clickable(onClick = onContinue).padding(12.dp),
        )
    }
}
