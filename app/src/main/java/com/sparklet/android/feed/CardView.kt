package com.sparklet.android.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sparklet.android.model.FeedCard

// Each card fills exactly one pager page (see FeedScreen's VerticalPager),
// but body length varies (~40-80 words, occasionally more) and the page
// height doesn't. Unlike a nested scrollable, this is just clipped for now
// rather than scrollable within its page — same known layout gap iOS's
// CardView documents (a nested scroll fights the pager's own drag gesture).
@Composable
fun CardView(card: FeedCard) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row {
            Text(card.category.icon)
            Spacer(modifier = Modifier.padding(start = 4.dp))
            Text(card.category.name, style = MaterialTheme.typography.labelMedium, color = Color.Gray)
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
                    .background(Color.LightGray),
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
        }

        Text(card.title, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.padding(top = 8.dp))
        Text(card.body, style = MaterialTheme.typography.bodyLarge, color = Color.DarkGray)

        card.sources.firstOrNull()?.let { source ->
            Spacer(modifier = Modifier.padding(top = 8.dp))
            val uriHandler = LocalUriHandler.current
            Text(
                source.publisher,
                style = MaterialTheme.typography.labelSmall.copy(textDecoration = TextDecoration.Underline),
                modifier = Modifier.clickable { uriHandler.openUri(source.url) },
            )
        }
    }
}
