package com.sparklet.android.friends

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.FriendRow
import com.sparklet.android.ui.theme.SparkletColors

@Composable
fun FriendsScreen(viewModel: FriendsViewModel) {
    val response by viewModel.response.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    var entry by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Friends",
            style = MaterialTheme.typography.titleLarge,
            color = SparkletColors.TextPrimary,
        )

        val current = response
        if (current != null) {
            Text(
                "Your friend code: ${current.friendCode}",
                style = MaterialTheme.typography.bodySmall,
                color = SparkletColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = entry,
                onValueChange = {
                    entry = it
                    viewModel.clearStatus()
                },
                singleLine = true,
                placeholder = {
                    Text("Email or friend code", color = SparkletColors.TextMuted)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SparkletColors.TextPrimary,
                    unfocusedTextColor = SparkletColors.TextPrimary,
                    focusedBorderColor = SparkletColors.Accent,
                    unfocusedBorderColor = SparkletColors.Border,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                "Add",
                style = MaterialTheme.typography.labelLarge,
                color = SparkletColors.AccentText,
                modifier = Modifier
                    .clickable {
                        viewModel.sendRequest(entry)
                        entry = ""
                    }
                    .padding(12.dp),
            )
        }

        statusMessage?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = SparkletColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (current == null && isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = SparkletColors.TextTertiary) }
            return@Column
        }
        if (current == null) {
            Text(
                "Couldn't load your friends.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            if (current.incoming.isNotEmpty()) {
                item { SectionTitle("Requests") }
                items(current.incoming, key = { "in-${it.friendshipId}" }) { row ->
                    FriendCard(
                        row = row,
                        primaryLabel = "Accept",
                        onPrimary = { viewModel.accept(row.friendshipId) },
                        secondaryLabel = "Decline",
                        onSecondary = { viewModel.remove(row.friendshipId) },
                    )
                }
            }

            if (current.outgoing.isNotEmpty()) {
                item { SectionTitle("Sent") }
                items(current.outgoing, key = { "out-${it.friendshipId}" }) { row ->
                    FriendCard(
                        row = row,
                        secondaryLabel = "Cancel",
                        onSecondary = { viewModel.remove(row.friendshipId) },
                    )
                }
            }

            item { SectionTitle("Friends") }
            if (current.friends.isEmpty()) {
                item {
                    Text(
                        "No friends yet — share your friend code above, or add someone by email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SparkletColors.TextTertiary,
                    )
                }
            } else {
                items(current.friends, key = { "f-${it.friendshipId}" }) { row ->
                    FriendCard(
                        row = row,
                        secondaryLabel = "Remove",
                        onSecondary = { viewModel.remove(row.friendshipId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = SparkletColors.TextPrimary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun FriendCard(
    row: FriendRow,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SparkletColors.Panel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = SparkletColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.email,
                style = MaterialTheme.typography.labelSmall,
                color = SparkletColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (primaryLabel != null && onPrimary != null) {
            Text(
                primaryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = SparkletColors.AccentText,
                modifier = Modifier.clickable(onClick = onPrimary).padding(8.dp),
            )
        }
        if (secondaryLabel != null && onSecondary != null) {
            Text(
                secondaryLabel,
                style = MaterialTheme.typography.labelLarge,
                color = SparkletColors.TextTertiary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, SparkletColors.Border, RoundedCornerShape(8.dp))
                    .clickable(onClick = onSecondary)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
