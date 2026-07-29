package com.sparklet.android

import android.app.Application
import com.sparklet.android.auth.AuthSession

// Owns AuthSession at the process level so it survives MainActivity being
// recreated (rotation, process restore) rather than re-reading the token
// store on every Activity creation.
class SparkletApplication : Application() {
    val authSession by lazy { AuthSession(this) }
}
