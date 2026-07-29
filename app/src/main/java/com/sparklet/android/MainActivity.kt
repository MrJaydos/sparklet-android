package com.sparklet.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sparklet.android.auth.AuthRedirect
import com.sparklet.android.auth.LoginScreen
import com.sparklet.android.config.AppConfig
import com.sparklet.android.feed.FeedScreen
import com.sparklet.android.ui.theme.SparkletTheme

class MainActivity : ComponentActivity() {
    private val authSession by lazy { (application as SparkletApplication).authSession }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    // Fired when the sparklet-android://auth?code=... redirect arrives
    // while this Activity is already running — guaranteed by launchMode=
    // singleTask in the manifest, which is what keeps the in-flight sign-in
    // state (AuthRedirect.beginAwaiting()) alive instead of a fresh Activity
    // instance replacing it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthRedirect(intent)
    }

    // See AuthRedirect's comment: this only signals "cancelled" if a
    // redirect was being awaited and onNewIntent didn't just handle it —
    // onNewIntent always runs before onResume when the OS delivers the
    // redirect intent, so ordering here is safe.
    override fun onResume() {
        super.onResume()
        AuthRedirect.onActivityResumed()
    }

    private fun handleAuthRedirect(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == AppConfig.AUTH_CALLBACK_SCHEME) {
            AuthRedirect.onRedirect(uri)
        }
    }
}
