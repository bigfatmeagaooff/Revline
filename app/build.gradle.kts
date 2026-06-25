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
        versionCode = 13
        versionName = "3.5.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Server base URL is NOT hardcoded — set -PrevlineApiBaseUrl=http://YOUR_IP/ at
        // build time (or REVLINE_API_BASE_URL in ~/.gradle/gradle.properties). Defaults to
        // the Android emulator's host loopback for local dev.
        val apiBaseUrl = (project.findProperty("revlineApiBaseUrl") as String?)
            ?: System.getenv("REVLINE_API_BASE_URL")
            ?: "http://10.0.2.2:3000/"
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
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
}
