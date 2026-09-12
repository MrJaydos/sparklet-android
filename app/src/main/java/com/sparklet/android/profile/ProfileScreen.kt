package com.sparklet.android.profile

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.sparklet.android.model.Badge
import com.sparklet.android.model.ProfileDetailsResponse
import com.sparklet.android.ui.theme.SparkletColors
import com.sparklet.android.ui.theme.categoryColor

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val details by viewModel.details.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadIfNeeded() }

    val current = details
    when {
        current == null && isLoading -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = SparkletColors.TextTertiary) }

        current == null -> Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Couldn't load your profile.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
            )
        }

        else -> ProfileContent(current, onSignOut = viewModel::signOut)
    }
}

@Composable
private fun ProfileContent(details: ProfileDetailsResponse, onSignOut: () -> Unit) {
    var confirmingSignOut by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        details.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = SparkletColors.TextPrimary,
                    )
                    Text(
                        details.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = SparkletColors.TextMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (details.premium) {
                        Text(
                            "PREMIUM",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SparkletColors.AccentText,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(1.dp, SparkletColors.Border, CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    // Up here, not at the end of the list: History is
                    // unbounded, so a sign-out below it sat behind ~8 swipes
                    // and was reported as "no way to sign out that I can
                    // tell". Anything a user needs to find on purpose cannot
                    // live past an infinite list.
                    Text(
                        "Sign out",
                        style = MaterialTheme.typography.labelLarge,
                        color = SparkletColors.DangerText,
                        modifier = Modifier
                            .padding(top = if (details.premium) 8.dp else 0.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, SparkletColors.Border, RoundedCornerShape(8.dp))
                            .clickable { confirmingSignOut = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("⚡", "${details.xp}", "XP", Modifier.weight(1f))
                StatTile("🔥", "${details.currentStreak}", "streak", Modifier.weight(1f))
                StatTile("📚", "${details.totalViewed}", "learned", Modifier.weight(1f))
                StatTile("🔁", "${details.dueReviews}", "due", Modifier.weight(1f))
            }
        }

        item { SectionTitle("Badges") }
        if (details.badges.isEmpty()) {
            item { EmptyNote("No badges yet — they arrive as you read, answer and keep a streak.") }
        } else {
            itemsIndexed(details.badges, key = { _, b -> b.key }) { _, badge -> BadgeRow(badge) }
        }

        item { SectionTitle("Top topics") }
        if (details.topCategories.isEmpty()) {
            item { EmptyNote("Read a few cards and your most-read topics show up here.") }
        } else {
            itemsIndexed(details.topCategories, key = { i, c -> "${c.name}-$i" }) { _, category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SparkletColors.Panel)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${category.icon} ${category.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = categoryColor(category.colorHex),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${category.count}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SparkletColors.TextTertiary,
                    )
                }
            }
        }

        item { SectionTitle("Notebook") }
        if (details.savedCards.isEmpty()) {
            item {
                EmptyNote(
                    "Tap the save icon on a card to add it to your notebook — your deliberate keep-list."
                )
            }
        } else {
            itemsIndexed(details.savedCards, key = { _, c -> c.cardId }) { _, card ->
                TitleRow(card.icon, card.title, card.colorHex)
            }
        }

        item { SectionTitle("History") }
        if (details.history.isEmpty()) {
            item {
                EmptyNote(
                    "Every card you view ends up here, so nothing is ever lost to a scroll or a refresh."
                )
            }
        } else {
            // Keyed by index, not cardId: history repeats a card whenever one
            // is re-read after a spaced-repetition review.
            itemsIndexed(details.history) { index, entry ->
                TitleRow(entry.icon, entry.title, entry.colorHex, key = index)
            }
        }

    }

    // Prominent placement plus a one-tap confirm: signing back in means the
    // whole Custom Tab round trip (and, with the PWA installed, a detour
    // through it), so an accidental tap is expensive to undo.
    if (confirmingSignOut) {
        AlertDialog(
            onDismissRequest = { confirmingSignOut = false },
            containerColor = SparkletColors.Panel,
            title = { Text("Sign out?", color = SparkletColors.TextPrimary) },
            text = {
                Text(
                    "You'll need to sign in again to get back to your feed.",
                    color = SparkletColors.TextTertiary,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingSignOut = false
                    onSignOut()
                }) {
                    Text("Sign out", color = SparkletColors.DangerText)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingSignOut = false }) {
                    Text("Cancel", color = SparkletColors.TextTertiary)
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = SparkletColors.TextPrimary,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = SparkletColors.TextTertiary)
}

@Composable
private fun StatTile(icon: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SparkletColors.Panel)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(icon, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = SparkletColors.TextPrimary,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = SparkletColors.TextMuted)
    }
}

@Composable
private fun BadgeRow(badge: Badge) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SparkletColors.Panel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(badge.icon, style = MaterialTheme.typography.titleMedium)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                badge.name,
                style = MaterialTheme.typography.bodyMedium,
                // Unearned badges stay legible but visibly secondary.
                color = if (badge.earnedTier != null) SparkletColors.TextPrimary
                else SparkletColors.TextTertiary,
            )
            val tier = badge.earnedTier
            val next = badge.nextTier
            Text(
                when {
                    tier != null && next != null ->
                        "${tier.label} · ${badge.value}/${next.threshold} to ${next.label}"
                    tier != null -> tier.label
                    next != null -> "${badge.value}/${next.threshold} to ${next.label}"
                    else -> "${badge.value}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = SparkletColors.TextMuted,
            )
        }
    }
}

@Composable
private fun TitleRow(icon: String, title: String, colorHex: String, key: Int? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SparkletColors.Panel)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = categoryColor(colorHex))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = SparkletColors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
