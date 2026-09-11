package com.sparklet.android.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sparklet.android.ui.theme.SparkletColors
import com.sparklet.android.ui.theme.categoryColor
import com.sparklet.android.model.DepthLevel
import com.sparklet.android.model.FeedCard
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.CardActionsApi
import kotlinx.coroutines.launch

// Each card fills exactly one pager page (see FeedScreen's VerticalPager),
// but body length varies (~40-80 words, occasionally more) and the page
// height doesn't. Unlike a nested scrollable, this is just clipped for now
// rather than scrollable within its page — same known layout gap iOS's
// CardView documents (a nested scroll fights the pager's own drag gesture).
@Composable
fun CardView(
    card: FeedCard,
    token: String?,
    onOpenComments: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenRelated: (String) -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember { FeedPreferences(context) }
    val actionsApi = remember { CardActionsApi() }
    val scope = rememberCoroutineScope()

    // The depth variant currently shown. Only the title/body are swapped —
    // every action keeps targeting the original card's id, same as the web
    // (see DepthCardResponse).
    var shownTitle by remember(card.id) { mutableStateOf(card.title) }
    var shownBody by remember(card.id) { mutableStateOf(card.body) }
    var shownDepth by remember(card.id) { mutableStateOf(card.depthLevel) }
    var depthError by remember(card.id) { mutableStateOf<String?>(null) }
    var isLoadingDepth by remember(card.id) { mutableStateOf(false) }

    fun applyDepth(level: DepthLevel, persist: Boolean) {
        if (level == shownDepth || isLoadingDepth) return
        isLoadingDepth = true
        depthError = null
        scope.launch {
            try {
                val response = actionsApi.fetchDepth(card.id, level, token)
                shownTitle = response.card.title
                shownBody = response.card.body
                shownDepth = response.card.depthLevel
                if (persist) preferences.depth = level
            } catch (e: ApiException.Server) {
                // 402 is the premium gate on deeper levels, not a failure —
                // say what it is rather than "something went wrong".
                depthError = if (e.status == 402) "That depth is part of premium."
                else "Couldn't switch depth."
            } catch (e: Exception) {
                depthError = "Couldn't switch depth."
            } finally {
                isLoadingDepth = false
            }
        }
    }

    // Auto-apply a remembered depth as cards scroll into view, matching the
    // web's LearnCard effect. STANDARD (an explicit reset) and null (never
    // set) both mean "leave it alone", which is why this isn't just a null
    // check.
    LaunchedEffect(card.id) {
        val preferred = preferences.depth
        if (preferred != null && preferred != DepthLevel.STANDARD && preferred != card.depthLevel) {
            applyDepth(preferred, persist = false)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Text(card.category.icon)
            Spacer(modifier = Modifier.padding(start = 4.dp))
            Text(
                card.category.name,
                style = MaterialTheme.typography.labelMedium,
                color = categoryColor(card.category.colorHex),
            )
        }

        Spacer(modifier = Modifier.padding(top = 8.dp))

        card.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = card.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SparkletColors.PanelAlt),
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
        }

        Text(shownTitle, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(shownBody, style = MaterialTheme.typography.bodyLarge, color = SparkletColors.TextSecondary)

        DepthSwitcher(
            current = shownDepth,
            isLoading = isLoadingDepth,
            onSelect = { applyDepth(it, persist = true) },
        )
        depthError?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.labelSmall,
                color = SparkletColors.TextTertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        CardActionsRail(
            card = card,
            token = token,
            onOpenComments = onOpenComments,
            onOpenReport = onOpenReport,
        )

        // Related links are the entry point to CardDetailSheet; the feed
        // card itself stays a single non-scrolling page.
        if (card.related.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                for (link in card.related.take(2)) {
                    Text(
                        "${link.icon} ${link.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SparkletColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenRelated(link.id) }
                            .padding(end = 8.dp),
                    )
                }
            }
        }

        card.sources.firstOrNull()?.let { source ->
            Spacer(modifier = Modifier.padding(top = 8.dp))
            val uriHandler = LocalUriHandler.current
            Text(
                source.publisher,
                style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                color = SparkletColors.TextMuted,
                modifier = Modifier.clickable { uriHandler.openUri(source.url) },
            )
        }
    }
}

// Four fixed levels, so a row of chips rather than a dropdown — the current
// one is always visible, which is the point.
@Composable
private fun DepthSwitcher(
    current: DepthLevel,
    isLoading: Boolean,
    onSelect: (DepthLevel) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (level in DepthLevel.entries) {
            val selected = level == current
            Text(
                level.shortLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else SparkletColors.TextMuted,
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (selected) Modifier.background(SparkletColors.Accent)
                        else Modifier.border(1.dp, SparkletColors.Border, CircleShape)
                    )
                    .clickable(enabled = !isLoading) { onSelect(level) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

private fun DepthLevel.shortLabel(): String = when (this) {
    DepthLevel.SIMPLE -> "Simple"
    DepthLevel.STANDARD -> "Standard"
    DepthLevel.DEEP -> "Deep"
    DepthLevel.EXTRA_DEEP -> "Extra"
}
