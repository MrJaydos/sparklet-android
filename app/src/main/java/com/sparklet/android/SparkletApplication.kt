package com.sparklet.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.sparklet.android.auth.AuthSession
import okhttp3.OkHttpClient

// Owns AuthSession at the process level so it survives MainActivity being
// recreated (rotation, process restore) rather than re-reading the token
// store on every Activity creation.
//
// Also the app-wide Coil image source (ImageLoaderFactory is Coil's
// documented hook for this — it auto-detects an Application implementing
// it). Card images are Wikimedia-hosted, and Wikimedia's User-Agent policy
// (meta.wikimedia.org/wiki/User-Agent_policy) 403s any request carrying
// OkHttp's bare default UA — the same reason the backend's own content
// importer sets a custom one (see sparklet's scripts/seed-content.ts).
// Without this, every card image silently fails to load.
class SparkletApplication : Application(), ImageLoaderFactory {
    val authSession by lazy { AuthSession(this) }

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Sparklet-Android/1.0 (+https://sparkletapp.com)")
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(this).okHttpClient(client).build()
    }
}
