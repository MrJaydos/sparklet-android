package com.sparklet.android.config

import com.sparklet.android.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl

object AppConfig {
    // Defaults to production (https://sparkletapp.com), where the mobile-auth
    // contract is actually deployed. Point it somewhere else without touching
    // this file — set `sparklet.apiBaseUrl` in local.properties (gitignored)
    // or pass -Psparklet.apiBaseUrl=...; see app/build.gradle.kts. It used to
    // be hardcoded to the emulator alias 10.0.2.2, which is unreachable from a
    // physical device, so every device build failed every request silently.
    val apiBaseUrl = BuildConfig.API_BASE_URL.toHttpUrl()

    // Must exactly match an entry in the backend's ALLOWED_MOBILE_SCHEMES
    // (sparklet/src/lib/mobile-auth.ts) and the intent-filter data element in
    // AndroidManifest.xml. Note the failure mode if it doesn't: /login does
    // NOT reject an unlisted scheme (it just falls through to an ordinary web
    // login — see src/app/login/page.tsx), so a typo here means sign-in
    // appears to succeed in the browser while the app waits forever for a
    // redirect that is never sent. The 400 lives at /api/auth/mobile-complete.
    const val AUTH_CALLBACK_SCHEME = "sparklet-android"
}
