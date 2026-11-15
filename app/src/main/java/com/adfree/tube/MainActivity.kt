package com.adfree.tube

import android.app.Activity
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<Button>()
    private lateinit var tabsBar: LinearLayout
    private val sections = arrayOfNulls<Section>(5)
    private val names = listOf("YouTube", "Live TV", "Movies", "Downloads", "All-in-One")
    private var currentIndex = -1
    private var isTv = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uiMode = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        requestedOrientation = if (isTv) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(6, 13, 25))
        }
        content = FrameLayout(this)
        tabsBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(12, 24, 42), Color.rgb(7, 15, 28)))
        }
        names.forEachIndexed { i, _ ->
            val b = Button(this).apply {
                text = when (i) {
                    0 -> "▶ YouTube"
                    1 -> "▣ TV"
                    2 -> "▰ Movies"
                    3 -> "✓ Downloads"
                    else -> "⬇ All-in-One"
                }
                textSize = if (isTv) 14f else 9f
                isAllCaps = false
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                background = navBackground(if (i == 0) "#E32636" else "#17263D", 16)
                elevation = dp(2).toFloat()
                minHeight = dp(if (isTv) 58 else 46)
                isFocusable = true
                setOnClickListener { show(i) }
            }
            tabs.add(b)
            tabsBar.addView(b, LinearLayout.LayoutParams(0, dp(if (isTv) 58 else 46), 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }
        val contentParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        if (isTv) { root.addView(tabsBar); root.addView(content, contentParams) }
        else { root.addView(content, contentParams); root.addView(tabsBar) }
        setContentView(root)
        show(0)
        tabs[0].requestFocus()
    }

    private fun sectionAt(i: Int): Section {
        var s = sections[i]
        if (s == null) {
            s = when (i) {
                0 -> YoutubeSection(this)
                1 -> TvSection(this, isTv) { fullscreen ->
                    tabsBar.visibility = if (fullscreen) View.GONE else View.VISIBLE
                }
                2 -> MoviesSection(this)
                3 -> DownloadSection(this)
                else -> AllInOneDownloaderSection(this)
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
        content.addView(s.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        s.onShow()
        currentIndex = index
        tabs.forEachIndexed { i, b ->
            b.alpha = if (i == index) 1f else 0.78f
            b.background = navBackground(if (i == index) "#E32636" else "#17263D", 16)
        }
        tabsBar.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun navBackground(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.parseColor("#344963"))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val s = if (currentIndex >= 0) sections[currentIndex] else null
        if (s != null && s.onBack()) return
        if (currentIndex != 0) { show(0); return }
        super.onBackPressed()
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
