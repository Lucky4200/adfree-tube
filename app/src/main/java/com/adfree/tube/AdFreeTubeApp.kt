package com.adfree.tube

import android.app.Application
import io.github.edsuns.adfilter.AdFilter

class AdFreeTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val filter = AdFilter.create(this)
        if (!filter.hasInstallation) {
            val subscriptions = linkedMapOf(
                "AdGuard Base" to "https://filters.adtidy.org/extension/chromium/filters/2.txt",
                "EasyList" to "https://easylist.to/easylist/easylist.txt",
                "EasyPrivacy" to "https://easylist.to/easylist/easyprivacy.txt",
                "AdGuard Tracking Protection" to "https://filters.adtidy.org/extension/chromium/filters/3.txt",
                "AdGuard Annoyances" to "https://filters.adtidy.org/extension/chromium/filters/14.txt"
            )
            subscriptions.forEach { (name, url) ->
                val item = filter.viewModel.addFilter(name, url)
                filter.viewModel.download(item.id)
            }
        }
    }
}
