package com.sparklet.android.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sparklet.android.model.FeedCard
import com.sparklet.android.network.CardActionsApi
import com.sparklet.android.ui.theme.SparkletColors
import com.sparklet.android.ui.theme.categoryColor

// Standalone single-card view, reached by tapping a related-card link.
//
// Unlike the feed's CardView this is scrollable and has no read-tracking:
// opening a card here is browsing, not the settled-on-screen dwell the
// interactions flow is built around, and counting it would inflate reads
// for cards the user only glanced at from a link.
@Composable
fun CardDetailSheet(cardId: String, token: String?, onOpenRelated: (String) -> Unit) {
    val api = remember { CardActionsApi() }
    var card by remember(cardId) { mutableStateOf<FeedCard?>(null) }
    var failed by remember(cardId) { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        try {
            card = api.fetchCard(cardId, token)
        } catch (e: Exception) {
            failed = true
        }
    }

    val current = card
    when {
        current != null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row {
                Text(current.category.icon)
                Text(
                    current.category.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = categoryColor(current.category.colorHex),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            current.imageUrl?.let { imageUrl ->
                AsyncImage(
                    model = imageUrl,
                    contentDescription = current.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SparkletColors.PanelAlt),
                )
            }

            Text(
                current.title,
                style = MaterialTheme.typography.headlineSmall,
                color = SparkletColors.TextPrimary,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                current.body,
                style = MaterialTheme.typography.bodyLarge,
                color = SparkletColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            val uriHandler = LocalUriHandler.current
            for (source in current.sources) {
                Text(
                    source.publisher,
                    style = MaterialTheme.typography.labelSmall
                        .copy(textDecoration = TextDecoration.Underline),
                    color = SparkletColors.TextMuted,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { uriHandler.openUri(source.url) },
                )
            }

            if (current.related.isNotEmpty()) {
                Text(
                    "Related",
                    style = MaterialTheme.typography.titleMedium,
                    color = SparkletColors.TextPrimary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
                for (link in current.related) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SparkletColors.Panel)
                            // Re-targets this same sheet rather than stacking
                            // another on top, so following a chain of related
                            // cards can't bury the user under sheets.
                            .clickable { onOpenRelated(link.id) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(link.icon)
                        Text(
                            link.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SparkletColors.TextSecondary,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
        }

        failed -> Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text(
                "Couldn't load that card.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
            )
        }

        else -> Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = SparkletColors.TextTertiary) }
    }
}
