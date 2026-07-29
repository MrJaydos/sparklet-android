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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(authSession: AuthSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { LoginController() }
    var isSigningIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text("Sparklet", style = MaterialTheme.typography.headlineLarge)

        Button(
            onClick = {
                scope.launch {
                    isSigningIn = true
                    errorMessage = null
                    try {
                        val token = controller.signIn { uri ->
                            CustomTabsIntent.Builder().build().launchUrl(context, uri)
                        }
                        authSession.signIn(token)
                    } catch (e: AuthException.Cancelled) {
                        errorMessage = "Sign-in was cancelled."
                    } catch (e: CancellationException) {
                        throw e // Composable left composition mid-sign-in — propagate, don't swallow.
                    } catch (e: Exception) {
                        errorMessage = e.message ?: "Sign-in failed."
                    } finally {
                        isSigningIn = false
                    }
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

        errorMessage?.let { message ->
            Text(message, color = Color.Red, style = MaterialTheme.typography.bodySmall)
        }
    }
}
