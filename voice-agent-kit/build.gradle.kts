plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    `maven-publish`
}

android {
    namespace = "com.voiceagent.kit"
    compileSdk = 33

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    // LiveKit — compileOnly since DDFin already has it
    compileOnly(libs.livekit.android)

    // Room — api so host app can use the same version
    api(libs.androidx.room.runtime)
    kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.common)

    // Retrofit — compileOnly, DDFin already has it
    compileOnly(libs.retrofit)
    compileOnly(libs.retrofit.gson)
    compileOnly(libs.okhttp.logging)

    // Firebase — compileOnly, DDFin already has it
    compileOnly(libs.firebase.analytics)

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.voiceagent"
                artifactId = "voice-agent-kit"
                version = "1.0.0"
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/chaithu-30/voice-agent-kit")
                credentials {
                    username = project.findProperty("gpr.user") as String? ?: System.getenv("chaithu-30")
                    password = project.findProperty("gpr.key") as String? ?: System.getenv("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")
                }
            }
        }
    }
}
