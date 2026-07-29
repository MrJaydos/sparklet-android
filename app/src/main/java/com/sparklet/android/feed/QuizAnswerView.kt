package com.sparklet.android.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.Category
import com.sparklet.android.model.QuizAnswerResponse
import com.sparklet.android.model.XpSummary
import com.sparklet.android.network.ApiException
import com.sparklet.android.network.ChallengeApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class QuizVariant { CHECKPOINT, REVIEW }

// Renders both plain checkpoint quizzes and due spaced-repetition reviews
// rendered as a question (same shape, different endpoint — see
// sparklet/src/app/api/reviews/[id]/answer/route.ts's comment). Mirrors
// web's QuizView.tsx.
@Composable
fun QuizAnswerView(
    id: String,
    question: String,
    options: List<String>,
    category: Category,
    variant: QuizVariant,
    token: String?,
    onResult: (XpSummary) -> Unit,
    onContinue: () -> Unit,
) {
    val api = remember { ChallengeApi() }
    val scope = rememberCoroutineScope()
    var picked by remember(id) { mutableStateOf<Int?>(null) }
    var result by remember(id) { mutableStateOf<QuizAnswerResponse?>(null) }
    // A review whose source card stopped being due between feed-fetch and
    // tap — the server never trusts client-claimed due state (see the
    // reviews/[id]/answer route's comment), so this is an expected race,
    // not a real failure. Skip forward rather than showing an error dialog.
    var alreadyHandled by remember(id) { mutableStateOf(false) }

    fun answer(index: Int) {
        if (picked != null) return
        picked = index
        scope.launch {
            try {
                val response = if (variant == QuizVariant.REVIEW) {
                    api.answerReviewQuiz(id, index, token)
                } else {
                    api.answerQuiz(id, index, token)
                }
                result = response
                onResult(response.xp)
            } catch (e: ApiException.Server) {
                if (variant == QuizVariant.REVIEW && e.status == 409) {
                    alreadyHandled = true
                }
                // Other failures: leave `picked` highlighted: the user can
                // still swipe on, same as web's fetch-catch behavior.
            } catch (e: Exception) {
                // Best-effort — no result panel, but the pager isn't blocked.
            }
        }
    }

    if (alreadyHandled) {
        LaunchedEffect(id) {
            delay(800)
            onContinue()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (alreadyHandled) {
            Text("Already reviewed — moving on", style = MaterialTheme.typography.bodyLarge)
            return@Column
        }

        Text(
            if (variant == QuizVariant.REVIEW) "🔁 Review — do you remember?" else "🧠 Quick recall · ${category.name}",
            style = MaterialTheme.typography.labelMedium,
            color = Color(android.graphics.Color.parseColor(category.colorHex)),
        )
        Text(
            question,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )

        options.forEachIndexed { index, option ->
            val current = result
            val background = when {
                current != null && index == current.correctIndex -> Color(0xFF1B5E20)
                current != null && index == picked -> Color(0xFFB71C1C)
                current != null -> Color(0xFF303030)
                index == picked -> Color(0xFF4527A0)
                else -> Color(0xFF212121)
            }
            Text(
                option,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .clickable(enabled = picked == null) { answer(index) }
                    .padding(16.dp),
            )
        }

        result?.let { r ->
            Column(modifier = Modifier.padding(top = 16.dp)) {
                Text(
                    if (r.correct) "✅ Nailed it" else "💡 Good try — now you know",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(r.explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                XpRewardText(r.xp, r.combo, r.multiplier)
                Button(onClick = onContinue, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Keep scrolling")
                }
            }
        }
    }
}

// Shared "+N XP" / combo readout, reused by all four answer views.
@Composable
fun XpRewardText(xp: XpSummary, combo: Int, multiplier: Double) {
    if (xp.awarded <= 0) return
    val comboText = if (combo >= 3) " · ${combo}🔥 combo (×$multiplier)" else ""
    Text(
        "+${xp.awarded} XP$comboText",
        style = MaterialTheme.typography.labelLarge,
        color = Color(0xFFFFC107),
        modifier = Modifier.padding(top = 8.dp),
    )
}
