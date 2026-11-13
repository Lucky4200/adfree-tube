plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adfree.tube"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.adfree.tube"
        // Android 7.1 is API 25. The native Brave engine is loaded only on API 26+.
        minSdk = 25
        targetSdk = 34
        versionCode = 10
        versionName = "1.0"
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

    // Brave native engine is used on Android 8.0+; Android 7.1 uses the built-in fallback blocker.
    implementation("io.github.mlm-games:adblock:0.1.3")
}
