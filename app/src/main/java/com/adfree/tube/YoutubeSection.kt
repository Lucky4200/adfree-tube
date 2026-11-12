package com.adfree.tube

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Single original YouTube entry. Alternative Piped/Invidious mirrors removed
 * as requested. Native SmartTube playback integration remains a separate task.
 */
class YoutubeSection(private val activity: Activity) : Section {

    private val chrome = FullscreenChrome(activity)
    private val web: WebView
    override val view: View

    init {
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL

        val home = Button(activity)
        home.text = "YouTube"
        home.isAllCaps = false
        home.setOnClickListener { web.loadUrl(Config.YOUTUBE_URL) }

        val reload = Button(activity)
        reload.text = "Reload"
        reload.isAllCaps = false
        reload.setOnClickListener { web.reload() }

        bar.addView(home, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(reload, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        web = WebFactory.create(
            activity, chrome,
            { host -> host.isNotBlank() },
            {
                Toast.makeText(activity, "YouTube load nahi hua. Internet check karein.", Toast.LENGTH_LONG).show()
            },
            {},
            keepMediaInBackground = true
        )

        col.addView(bar)
        col.addView(web, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        view = col
        web.loadUrl(Config.YOUTUBE_URL)
    }

    override fun onBack(): Boolean {
        if (chrome.hideIfShown()) return true

        // Detect an actively playing HTML5 video before navigating away.
        // This preserves the current YouTube watch page when the app is backgrounded.
        web.evaluateJavascript(
            "(function(){var v=document.querySelector('video');return !!(v && !v.paused && v.readyState>2);})()"
        ) { result ->
            val playing = result == "true"
            if (playing) {
                activity.moveTaskToBack(true)
            } else if (web.canGoBack()) {
                web.goBack()
            } else {
                activity.finish()
            }
        }
        return true
    }

    override fun onShow() = web.onResume()

    // Called when switching tabs, not when the Activity itself goes to background.
    override fun onHide() = web.onPause()
    override fun onDestroy() = web.destroy()
}
