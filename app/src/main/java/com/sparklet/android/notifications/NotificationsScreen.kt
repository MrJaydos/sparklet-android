package com.sparklet.android.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.NotificationAlert
import com.sparklet.android.model.NotificationItem
import com.sparklet.android.ui.theme.SparkletColors

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel) {
    val response by viewModel.response.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Notifications",
                style = MaterialTheme.typography.titleLarge,
                color = SparkletColors.TextPrimary,
            )
            Box(modifier = Modifier.weight(1f))
            if ((response?.unreadCount ?: 0) > 0) {
                Text(
                    "Mark all read",
                    style = MaterialTheme.typography.labelLarge,
                    color = SparkletColors.AccentText,
                    modifier = Modifier.clickable { viewModel.markAllRead() }.padding(8.dp),
                )
            }
        }

        val current = response
        when {
            current == null && isLoading -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = SparkletColors.TextTertiary) }

            current == null -> Text(
                "Couldn't load notifications.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
            )

            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(current.adminAlerts, key = { "admin-${it.id}" }) { AlertRow(it) }
                items(current.friendAlerts, key = { "friend-${it.id}" }) { AlertRow(it) }

                if (current.notifications.isEmpty() &&
                    current.adminAlerts.isEmpty() &&
                    current.friendAlerts.isEmpty()
                ) {
                    item {
                        Text(
                            "When someone replies in a comment thread you're part of, it shows up here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SparkletColors.TextTertiary,
                        )
                    }
                }

                items(current.notifications, key = { it.id }) { NotificationRow(it) }
            }
        }
    }
}

@Composable
private fun AlertRow(alert: NotificationAlert) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SparkletColors.Accent.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            alert.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = SparkletColors.AccentText,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
            "${alert.count}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = SparkletColors.AccentText,
        )
    }
}

@Composable
private fun NotificationRow(notification: NotificationItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // Unread gets the lifted panel; read falls back to the flat
            // background so the distinction survives without a badge.
            .background(if (notification.read) SparkletColors.Background else SparkletColors.Panel)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            "${notification.actorName} commented on ${notification.cardTitle}",
            style = MaterialTheme.typography.bodyMedium,
            color = SparkletColors.TextPrimary,
        )
        notification.preview?.let { preview ->
            Text(
                "“$preview”",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
