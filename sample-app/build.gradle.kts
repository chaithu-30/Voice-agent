plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.sample.voiceagent"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.sample.voiceagent"
        minSdk = 28
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Reference the local SDK module
    implementation(project(":voice-agent-kit"))

    // LiveKit — the sample app must provide it (SDK declares compileOnly)
    implementation(libs.livekit.android)

    // Firebase (BOM approach — sample app provides Firebase)
    implementation(libs.firebase.analytics)

    // Retrofit (sample app provides it)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // Core UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
