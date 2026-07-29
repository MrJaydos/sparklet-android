package com.sparklet.android.config

import okhttp3.HttpUrl.Companion.toHttpUrl

object AppConfig {
    // Swap to the local Next.js dev server (see AGENTS.md — runs on
    // PORT=3001) while iterating locally. From the emulator, the host
    // machine is 10.0.2.2, not localhost; from a physical device, use your
    // machine's LAN IP. Either way, see network_security_config.xml for the
    // matching cleartext allowance.
    val apiBaseUrl = "http://10.0.2.2:3001".toHttpUrl()

    // Must exactly match an entry in the backend's ALLOWED_MOBILE_SCHEMES
    // (sparklet/src/lib/mobile-auth.ts) and the intent-filter data element in
    // AndroidManifest.xml — it's a fixed allowlist, not a passthrough, so any
    // other value 400s at /login.
    const val AUTH_CALLBACK_SCHEME = "sparklet-android"
}
