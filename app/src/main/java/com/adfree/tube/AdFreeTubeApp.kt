package com.adfree.tube

import android.app.Application

class AdFreeTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The Brave Rust/JNI library requires API 26+. Android 7.1 uses AdBlock fallback.
        BraveCompat.initialize(this)
    }
}
