plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.revline.tracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.revline.tracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "3.9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Server base URL is NOT hardcoded — set -PrevlineApiBaseUrl=http://YOUR_IP/ at
        // build time (or REVLINE_API_BASE_URL in ~/.gradle/gradle.properties). Defaults to
        // the Android emulator's host loopback for local dev.
        val apiBaseUrl = (project.findProperty("revlineApiBaseUrl") as String?)
            ?: System.getenv("REVLINE_API_BASE_URL")
            ?: "http://10.0.2.2:3000/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")

        // Sentry crash reporting. Like the server URL, the DSN is NOT hardcoded — pass
        // -PrevlineSentryDsn=... at build time (or REVLINE_SENTRY_DSN in the env).
        // Blank => the Sentry SDK auto-init sees an empty DSN and stays fully disabled.
        // A Sentry DSN is a write-only ingest key that ships inside every APK anyway, so
        // it's fine to keep the project default here. Override per-build with
        // -PrevlineSentryDsn=... or REVLINE_SENTRY_DSN; pass an empty value to disable.
        val sentryDsn = (project.findProperty("revlineSentryDsn") as String?)
            ?: System.getenv("REVLINE_SENTRY_DSN")
            ?: "https://618c73f3b64c0a354b1e607e9b9e75e7@o4511989209497600.ingest.de.sentry.io/4511989261533264"
        manifestPlaceholders["sentryDsn"] = sentryDsn
        manifestPlaceholders["sentryEnv"] = if (sentryDsn.isBlank()) "development" else "production"
    }

    signingConfigs {
        // Stable debug key, committed to the repo so EVERY build — CI, any dev
        // machine — signs identically. Without this, each machine (and every fresh
        // CI runner) generates its own random debug key, so installed APKs can't be
        // updated in place and have to be uninstalled first. This is NOT the Play
        // Store release key; standard debug credentials, safe to commit.
        getByName("debug") {
            storeFile = file("revline-debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Debug APK is fine for v1 testing. See README for signed release steps.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        viewBinding = true
        buildConfig = true
    }
}

// Export Room schemas to source control — supports future migrations cleanly
// (the whole product hinges on adding features without data migrations).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)

    // Lifecycle + coroutines
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Location
    implementation(libs.play.services.location)

    // Route map (open-source, no API key / billing)
    implementation(libs.osmdroid.android)

    // Networking (Phase 3 — server sync)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.fragment.ktx)

    // Crash / error reporting (auto-captures uncaught exceptions + ANRs via manifest init)
    implementation(libs.sentry.android)
}
