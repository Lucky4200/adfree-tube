package com.adfree.tube

import android.app.Activity
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class YoutubeSection(private val activity: Activity) : Section {

    private val chrome = FullscreenChrome(activity)
    private val servers = Config.YOUTUBE_SERVERS
    private var idx = 0
    private var fails = 0
    private val serverBtn = Button(activity)
    private val web: WebView
    override val view: View

    init {
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL

        val home = Button(activity)
        home.text = "Home"
        home.isAllCaps = false
        home.setOnClickListener {
            fails = 0
            load()
        }
        serverBtn.isAllCaps = false
        serverBtn.setOnClickListener {
            fails = 0
            idx = (idx + 1) % servers.size
            load()
        }
        bar.addView(home, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(serverBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f))

        web = WebFactory.create(
            activity, chrome,
            { host -> hostAllowed(host) },
            { onError() },
            { fails = 0 }
        )

        col.addView(bar)
        col.addView(
            web,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        view = col
        load()
    }

    private fun hostAllowed(host: String): Boolean {
        val base = Uri.parse(servers[idx]).host ?: return false
        return host == base || host.endsWith(".$base")
    }

    private fun load() {
        serverBtn.text = "Server ${idx + 1}/${servers.size} (badlo)"
        web.loadUrl(servers[idx])
    }

    private fun onError() {
        fails++
        if (fails < servers.size) {
            idx = (idx + 1) % servers.size
            web.post { load() }
        } else {
            Toast.makeText(activity, "Koi YouTube server nahi khula. Internet check karein.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onBack(): Boolean {
        if (chrome.hideIfShown()) return true
        if (web.canGoBack()) {
            web.goBack()
            return true
        }
        return false
    }

    override fun onShow() = web.onResume()
    override fun onHide() = web.onPause()
    override fun onDestroy() = web.destroy()
}
