plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adfree.tube"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.adfree.tube"
        // Brave adblock-rust Android wrapper requires Android 8.0+.
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "0.8"
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")

    // Brave's Rust-based adblocking engine with Android JNI bindings.
    implementation("io.github.mlm-games:adblock:0.1.3")
}
