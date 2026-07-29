package com.sparklet.android.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sparklet.android.model.FeedGuess
import com.sparklet.android.model.GuessAnswerResponse
import com.sparklet.android.model.XpSummary
import com.sparklet.android.network.ChallengeApi
import kotlin.math.max
import kotlin.math.round
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Guess-before-reveal: predict a number on a slider, then see the real
// answer. Mirrors web's GuessView.tsx.
@Composable
fun GuessAnswerView(
    guess: FeedGuess,
    token: String?,
    onResult: (XpSummary) -> Unit,
    onContinue: () -> Unit,
) {
    val api = remember { ChallengeApi() }
    val scope = rememberCoroutineScope()
    val range = guess.max - guess.min
    // Whole-number answers get whole-number steps, capped at ~100
    // positions so a huge integer range isn't 1-at-a-time — same shape as
    // web's step derivation.
    val step = remember(guess.id) { if (guess.integer) max(1.0, round(range / 100)) else range / 100 }
    val stepsCount = remember(guess.id) { max(0, (range / step).roundToInt() - 1) }

    var value by remember(guess.id) { mutableStateOf(guess.min + range / 2) }
    var locked by remember(guess.id) { mutableStateOf(false) }
    var result by remember(guess.id) { mutableStateOf<GuessAnswerResponse?>(null) }

    fun withUnit(n: Double): String {
        val formatted = if (guess.integer) n.roundToInt().toString() else "%.2f".format(n)
        return if (guess.unit == "%") "$formatted%" else if (guess.unit.isNotBlank()) "$formatted ${guess.unit}" else formatted
    }

    fun lockIn() {
        if (locked) return
        locked = true
        scope.launch {
            try {
                val response = api.answerGuess(guess.id, value, token)
                result = response
                onResult(response.xp)
            } catch (e: Exception) {
                locked = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔮 Take a guess · ${guess.category.name}", style = MaterialTheme.typography.labelMedium)
        Text(
            guess.prompt,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
        )

        val current = result
        if (current == null) {
            Text(withUnit(value), style = MaterialTheme.typography.displaySmall)
            Slider(
                value = value.toFloat(),
                onValueChange = { value = it.toDouble() },
                valueRange = guess.min.toFloat()..guess.max.toFloat(),
                steps = stepsCount,
                enabled = !locked,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(withUnit(guess.min), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text(withUnit(guess.max), style = MaterialTheme.typography.labelSmall)
            }
            Button(onClick = ::lockIn, enabled = !locked, modifier = Modifier.padding(top = 16.dp)) {
                Text(if (locked) "…" else "Lock it in")
            }
        } else {
            Text(
                when {
                    current.accuracy >= 0.95 -> "🎯 Scary close!"
                    current.accuracy >= 0.85 -> "🎯 Great instincts"
                    current.accuracy >= 0.6 -> "👀 Not bad at all"
                    current.accuracy >= 0.3 -> "🤔 Further than you thought"
                    else -> "🤯 Way off — that's the fun part"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "${withUnit(current.answer)} — you said ${withUnit(value)}",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(current.explanation, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
            XpRewardText(current.xp, current.combo, current.multiplier)
            Button(onClick = onContinue, modifier = Modifier.padding(top = 12.dp)) {
                Text("Keep scrolling")
            }
        }
    }
}
