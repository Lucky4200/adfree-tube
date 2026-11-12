package com.adfree.tube

import android.app.Activity
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<Button>()
    private val sections = arrayOfNulls<Section>(3)
    private val names = listOf("▶ YouTube", "📺 TV", "🎬 Movies")
    private var currentIndex = -1
    private var isTv = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uiMode = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        // Keep the phone app portrait by default. TV fullscreen opts into the sensor explicitly.
        requestedOrientation = if (isTv) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Color.parseColor("#0B1E3F"))

        content = FrameLayout(this)

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER
        val textSize = if (isTv) 24f else 16f
        names.forEachIndexed { i, n ->
            val b = Button(this)
            b.text = n
            b.textSize = textSize
            b.isFocusable = true
            b.setOnClickListener { show(i) }
            tabs.add(b)
            bar.addView(
                b,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        val contentParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
        if (isTv) {
            root.addView(bar)
            root.addView(content, contentParams)
        } else {
            root.addView(content, contentParams)
            root.addView(bar)
        }

        setContentView(root)
        show(0)
        tabs[0].requestFocus()
    }

    private fun sectionAt(i: Int): Section {
        var s = sections[i]
        if (s == null) {
            s = when (i) {
                0 -> YoutubeSection(this)
                1 -> TvSection(this, isTv)
                else -> MoviesSection(this)
            }
            sections[i] = s
        }
        return s
    }

    private fun show(index: Int) {
        if (index == currentIndex) return
        if (currentIndex >= 0) sections[currentIndex]?.onHide()
        content.removeAllViews()
        val s = sectionAt(index)
        content.addView(
            s.view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        s.onShow()
        currentIndex = index
        tabs.forEachIndexed { i, b -> b.alpha = if (i == index) 1f else 0.6f }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val s = if (currentIndex >= 0) sections[currentIndex] else null
        if (s != null && s.onBack()) return
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
        // Do not pause the YouTube WebView here: when the user backgrounds
        // the app, an active video must be allowed to continue playing.
    }

    override fun onResume() {
        super.onResume()
        if (currentIndex >= 0) sections[currentIndex]?.onShow()
    }

    override fun onDestroy() {
        sections.forEach { it?.onDestroy() }
        super.onDestroy()
    }
}
