package com.sparklet.android.auth

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sparklet.android.ui.theme.SparkletColors

// Deliberately holds no sign-in state of its own and starts no coroutine.
// Everything it shows is derived from AuthSession.signInState, so the flow
// survives this composable being torn down and rebuilt mid-sign-in — which
// is routine, since sign-in leaves the app for a Custom Tab and a fold,
// rotation or low-memory kill while out there recreates the Activity. See
// AuthSession's comment for what that used to cost.
@Composable
fun LoginScreen(authSession: AuthSession) {
    val context = LocalContext.current
    val signInState by authSession.signInState.collectAsState()
    val isSigningIn = signInState is AuthSession.SignInState.InProgress

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Sparklet", style = MaterialTheme.typography.headlineLarge)

        Button(
            onClick = {
                authSession.beginSignIn { uri ->
                    CustomTabsIntent.Builder().build().launchUrl(context, uri)
                }
            },
            enabled = !isSigningIn,
        ) {
            if (isSigningIn) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
            } else {
                Text("Sign in")
            }
        }

        (signInState as? AuthSession.SignInState.Failed)?.let { failed ->
            Text(failed.message, color = SparkletColors.DangerText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
