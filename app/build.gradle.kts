import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing key: provided by the GitHub workflow through environment variables.
// Without it (e.g. someone builds a fork), the release build falls back to the debug key so the build still works.
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")
val hasReleaseKey = keystorePath != null && File(keystorePath).exists()

android {
    namespace = "com.forgerom.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.forgerom.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0-beta"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = File(keystorePath!!)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "forgerom"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    lint {
        // Community translations may be incomplete; missing strings fall back to English.
        checkReleaseBuilds = false
        abortOnError = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
