package com.sparklet.android.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.FeedMisconception
import com.sparklet.android.model.MisconceptionAnswerResponse
import com.sparklet.android.model.XpSummary
import com.sparklet.android.network.ChallengeApi
import kotlinx.coroutines.launch

// Predict-then-reveal: commit to true/false on a widely-believed claim,
// then see the correction. Mirrors web's MisconceptionView.tsx.
@Composable
fun MisconceptionAnswerView(
    misconception: FeedMisconception,
    token: String?,
    onResult: (XpSummary) -> Unit,
    onContinue: () -> Unit,
) {
    val api = remember { ChallengeApi() }
    val scope = rememberCoroutineScope()
    var locked by remember(misconception.id) { mutableStateOf(false) }
    var pickedGuess by remember(misconception.id) { mutableStateOf<Boolean?>(null) }
    var result by remember(misconception.id) { mutableStateOf<MisconceptionAnswerResponse?>(null) }

    fun lockIn(guess: Boolean) {
        if (locked) return
        locked = true
        pickedGuess = guess
        scope.launch {
            try {
                val response = api.answerMisconception(misconception.id, guess, token)
                result = response
                onResult(response.xp)
            } catch (e: Exception) {
                locked = false
                pickedGuess = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🤨 True or false? · ${misconception.category.name}", style = MaterialTheme.typography.labelMedium)
        Text(
            misconception.claim,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )

        val current = result
        if (current == null) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { lockIn(true) },
                    enabled = !locked,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                ) {
                    Text(if (locked && pickedGuess == true) "…" else "TRUE")
                }
                Button(
                    onClick = { lockIn(false) },
                    enabled = !locked,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) {
                    Text(if (locked && pickedGuess == false) "…" else "FALSE")
                }
            }
        } else {
            Text(
                if (current.correct) "🎯 Nailed it" else "😮 Actually ${if (current.answer) "TRUE" else "FALSE"}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "you said ${if (pickedGuess == true) "true" else "false"}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(current.explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            XpRewardText(current.xp, current.combo, current.multiplier)
            Button(onClick = onContinue, modifier = Modifier.padding(top = 12.dp)) {
                Text("Keep scrolling")
            }
        }
    }
}
