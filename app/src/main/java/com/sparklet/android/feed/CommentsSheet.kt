package com.sparklet.android.feed

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.Comment
import com.sparklet.android.network.CardActionsApi
import com.sparklet.android.ui.theme.SparkletColors
import kotlinx.coroutines.launch

@Composable
fun CommentsSheet(cardId: String, token: String?) {
    val api = remember { CardActionsApi() }
    val scope = rememberCoroutineScope()
    var comments by remember(cardId) { mutableStateOf<List<Comment>?>(null) }
    var draft by remember(cardId) { mutableStateOf("") }
    var isPosting by remember(cardId) { mutableStateOf(false) }

    LaunchedEffect(cardId) {
        comments = try {
            api.fetchComments(cardId, token).comments
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            "Comments",
            style = MaterialTheme.typography.titleLarge,
            color = SparkletColors.TextPrimary,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Add a comment", color = SparkletColors.TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = SparkletColors.TextPrimary,
                    unfocusedTextColor = SparkletColors.TextPrimary,
                    focusedBorderColor = SparkletColors.Accent,
                    unfocusedBorderColor = SparkletColors.Border,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                if (isPosting) "…" else "Post",
                style = MaterialTheme.typography.labelLarge,
                color = SparkletColors.AccentText,
                modifier = Modifier
                    .clickable(enabled = !isPosting && draft.isNotBlank()) {
                        val body = draft.trim()
                        draft = ""
                        isPosting = true
                        scope.launch {
                            try {
                                val posted = api.postComment(cardId, body, token)
                                // Prepend rather than re-fetching: the route
                                // returns the created comment, and a refetch
                                // would discard anything typed since.
                                comments = listOf(posted) + (comments ?: emptyList())
                            } catch (e: Exception) {
                                draft = body
                            } finally {
                                isPosting = false
                            }
                        }
                    }
                    .padding(12.dp),
            )
        }

        val current = comments
        when {
            current == null -> Box(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = SparkletColors.TextTertiary) }

            current.isEmpty() -> Text(
                "No comments yet — say the first thing.",
                style = MaterialTheme.typography.bodyMedium,
                color = SparkletColors.TextTertiary,
                modifier = Modifier.padding(top = 12.dp),
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                items(current, key = { it.id }) { comment ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SparkletColors.Panel)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            if (comment.mine) "${comment.author} · you" else comment.author,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (comment.mine) SparkletColors.AccentText
                            else SparkletColors.TextTertiary,
                        )
                        Text(
                            comment.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SparkletColors.TextSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}
