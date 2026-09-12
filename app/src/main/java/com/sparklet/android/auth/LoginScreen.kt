package com.sparklet.android.auth

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sparklet.android.ui.theme.SparkletColors

// Mirrors sparklet-ios's LoginView, which in turn mirrors the web login
// page's wordmark and subtitle (sparklet/src/app/login/page.tsx) — the same
// three elements in the same order, centred, over the shared dark ground.
//
// Deliberately holds no sign-in state of its own and starts no coroutine.
// Everything it shows is derived from AuthSession.signInState, so the flow
// survives this composable being torn down and rebuilt mid-sign-in — which is
// routine, since sign-in leaves the app for a Custom Tab and a fold, rotation
// or low-memory kill while out there recreates the Activity. See AuthSession's
// comment for what that used to cost.
@Composable
fun LoginScreen(authSession: AuthSession) {
    val context = LocalContext.current
    val signInState by authSession.signInState.collectAsState()
    val isSigningIn = signInState is AuthSession.SignInState.InProgress

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "✨ Sparklet",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = SparkletColors.TextPrimary,
        )
        Text(
            "Learn something real, one swipe at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = SparkletColors.TextTertiary,
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = {
                authSession.beginSignIn { uri ->
                    CustomTabsIntent.Builder().build().launchUrl(context, uri)
                }
            },
            enabled = !isSigningIn,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SparkletColors.Accent,
                contentColor = Color.White,
                // Material dims a disabled button towards the surface colour,
                // which on this background reads as the button vanishing while
                // the user waits. Hold the filled look and let the spinner
                // carry the "busy" signal instead.
                disabledContainerColor = SparkletColors.Accent,
                disabledContentColor = Color.White,
            ),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Sign in", style = MaterialTheme.typography.titleMedium)
            }
        }

        (signInState as? AuthSession.SignInState.Failed)?.let { failed ->
            Text(
                failed.message,
                style = MaterialTheme.typography.bodySmall,
                color = SparkletColors.DangerText,
                textAlign = TextAlign.Center,
            )
        }
    }
}
