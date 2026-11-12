package com.adfree.tube

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class MoviesSection(private val activity: Activity) : Section {

    private val chrome = FullscreenChrome(activity)
    private lateinit var web: WebView
    override val view: View

    init {
        val col = LinearLayout(activity)
        col.orientation = LinearLayout.VERTICAL

        val bar = LinearLayout(activity)
        bar.orientation = LinearLayout.HORIZONTAL
        val home = Button(activity)
        home.text = "Home"
        home.isAllCaps = false
        home.setOnClickListener { web.loadUrl(Config.MOVIES_URL) }
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
                Toast.makeText(activity, "Movies ka link nahi khula. Internet check karein.", Toast.LENGTH_LONG).show()
            },
            {}
        )

        col.addView(bar)
        col.addView(
            web,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        view = col
        web.loadUrl(Config.MOVIES_URL)
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
