package com.adfree.tube

import android.app.Application

class AdFreeTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        BraveAdBlock.initialize(this)
    }
}
