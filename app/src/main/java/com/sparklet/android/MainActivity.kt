package com.sparklet.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sparklet.android.auth.LoginScreen
import com.sparklet.android.config.AppConfig
import com.sparklet.android.feed.FeedScreen
import com.sparklet.android.invite.InviteLink
import com.sparklet.android.invite.InviteScreen
import com.sparklet.android.invite.InviteViewModel
import com.sparklet.android.ui.theme.SparkletTheme

class MainActivity : ComponentActivity() {
    private val authSession by lazy { (application as SparkletApplication).authSession }

    // Set when an invite link opened the app. Held on the Activity rather
    // than inside the composition so it survives being set from onCreate
    // (cold start via the link) as well as onNewIntent (already running).
    private var pendingInviteRefId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Also handles the case where the redirect is what started this
        // process at all — after a background kill there is no running
        // sign-in to resume, just a valid code to redeem. AuthSession takes
        // it from here regardless.
        handleAuthRedirect(intent)
        handleInviteLink(intent)

        setContent {
            SparkletTheme {
                val token by authSession.token.collectAsState()
                val refId = pendingInviteRefId
                when {
                    // Accepting an invite needs a session, so an invite that
                    // arrives while signed out falls through to login and is
                    // picked up once a token exists.
                    token != null && refId != null -> {
                        val inviteViewModel = viewModel { InviteViewModel(authSession) }
                        InviteScreen(
                            viewModel = inviteViewModel,
                            refId = refId,
                            onContinue = { pendingInviteRefId = null },
                        )
                    }

                    token != null -> FeedScreen(authSession)
                    else -> LoginScreen(authSession)
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
        handleInviteLink(intent)
    }

    // Only signals "cancelled" if a redirect was still expected and neither
    // onCreate nor onNewIntent just handled one — both run before onResume
    // when the OS delivers the redirect, so the ordering is safe. See
    // AuthSession.onActivityResumed.
    override fun onResume() {
        super.onResume()
        authSession.onActivityResumed()
    }

    // https://sparkletapp.com/invite/<refId>. The manifest declares this as
    // an auto-verified App Link, which only actually verifies once an
    // assetlinks.json for this package is served from the domain — until
    // then Android treats it as an ordinary web intent and may show a
    // chooser, the same caveat sparklet-ios documents for its Universal
    // Links entitlement.
    private fun handleInviteLink(intent: Intent) {
        val uri = intent.data ?: return
        InviteLink.refId(uri)?.let { pendingInviteRefId = it }
    }

    private fun handleAuthRedirect(intent: Intent) {
        val uri = intent.data ?: return
        if (uri.scheme == AppConfig.AUTH_CALLBACK_SCHEME) {
            authSession.onAuthRedirect(uri)
        }
    }
}
