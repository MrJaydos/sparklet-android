package com.sparklet.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sparklet.android.auth.LoginScreen
import com.sparklet.android.config.AppConfig
import com.sparklet.android.feed.FeedScreen
import com.sparklet.android.ui.theme.SparkletTheme

class MainActivity : ComponentActivity() {
    private val authSession by lazy { (application as SparkletApplication).authSession }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Also handles the case where the redirect is what started this
        // process at all — after a background kill there is no running
        // sign-in to resume, just a valid code to redeem. AuthSession takes
        // it from here regardless.
        handleAuthRedirect(intent)

        setContent {
            SparkletTheme {
                val token by authSession.token.collectAsState()
                if (token != null) {
                    FeedScreen(authSession)
                } else {
                    LoginScreen(authSession)
                }
            }
        }
    }

    // Fired when the sparklet-android://auth?code=... redirect arrives while
    // this Activity is already running — guaranteed by launchMode=singleTask
    // in the manifest, which keeps the redirect on this task rather than
    // spawning a second Activity instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    // Only signals "cancelled" if a redirect was still expected and neither
    // onCreate nor onNewIntent just handled one — both run before onResume
    // when the OS delivers the redirect, so the ordering is safe. See
    // AuthSession.onActivityResumed.
    override fun onResume() {
        super.onResume()
        authSession.onActivityResumed()
    }

    private fun handleAuthRedirect(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == AppConfig.AUTH_CALLBACK_SCHEME) {
            authSession.onAuthRedirect(uri)
        }
    }
}
