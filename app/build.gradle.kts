import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Which backend the client talks to. Defaults to production, which is where
// the mobile-auth contract is actually deployed. Override it *without*
// editing tracked source — that's the point of it living here rather than in
// AppConfig.kt, which previously carried a hardcoded emulator-only address
// that silently made every physical-device build unusable:
//
//   local.properties:  sparklet.apiBaseUrl=http://192.168.1.42:3001
//   or one-off:        ./gradlew :app:assembleDebug -Psparklet.apiBaseUrl=...
//
// Emulator reaches the host machine at 10.0.2.2; a physical device needs the
// machine's LAN IP. Debug builds permit cleartext to any host (see
// src/debug/res/xml/network_security_config.xml) so a LAN IP needs no further
// setup; release builds stay HTTPS-only.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val sparkletApiBaseUrl: String =
    (project.findProperty("sparklet.apiBaseUrl") as String?)
        ?: localProperties.getProperty("sparklet.apiBaseUrl")
        ?: "https://sparkletapp.com"

android {
    namespace = "com.sparklet.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sparklet.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        buildConfigField("String", "API_BASE_URL", "\"$sparkletApiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    // Custom Tabs — the external-user-agent OAuth flow the mobile auth
    // contract requires (see auth/LoginController.kt).
    implementation("androidx.browser:browser:1.8.0")

    // App-private, unencrypted key-value storage for the bearer token.
    // Sits behind auth/TokenStore.kt so swapping to EncryptedSharedPreferences
    // later is a one-file change if that's ever warranted.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Async image loading for CardView's imageUrl — Compose has no built-in
    // equivalent of SwiftUI's AsyncImage.
    implementation("io.coil-kt:coil-compose:2.7.0")
}
