package com.sparklet.android.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth")

// Holds the bearer token that stands in for the backend's
// Session.sessionToken (see sparklet/prisma/schema.prisma) once the mobile
// auth exchange completes — see LoginController and AGENTS.md's auth
// section for the full contract. Preferences DataStore is app-private,
// unencrypted storage; that's the same sandboxing model iOS's Keychain
// wrapper (KeychainTokenStore) provides for app-private data, just without
// the extra device-keystore-backed encryption layer.
class TokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val tokenKey = stringPreferencesKey("session_token")

    suspend fun load(): String? =
        appContext.authDataStore.data.map { it[tokenKey] }.first()

    suspend fun save(token: String) {
        appContext.authDataStore.edit { it[tokenKey] = token }
    }

    suspend fun clear() {
        appContext.authDataStore.edit { it.remove(tokenKey) }
    }
}
