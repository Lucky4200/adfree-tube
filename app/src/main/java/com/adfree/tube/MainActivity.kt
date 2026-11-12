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
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<Button>()
    private lateinit var tabsBar: LinearLayout
    private val sections = arrayOfNulls<Section>(3)
    private val names = listOf("YouTube", "Live TV", "Movies")
    private var currentIndex = -1
    private var isTv = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uiMode = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        requestedOrientation = if (isTv) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(6, 13, 25))
        }
        content = FrameLayout(this)

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(12, 24, 42), Color.rgb(7, 15, 28))
            )
        }
        tabsBar = bar
        names.forEachIndexed { i, _ ->
            val b = Button(this).apply {
                text = when (i) {
                    0 -> "▶   YouTube"
                    1 -> "▣   Live TV"
                    else -> "▰   Movies"
                }
                textSize = if (isTv) 17f else 13f
                isAllCaps = false
                setTextColor(Color.WHITE)
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                background = navBackground(if (i == 0) "#E32636" else "#17263D", 18)
                elevation = dp(2).toFloat()
                minHeight = dp(if (isTv) 58 else 48)
                isFocusable = true
                setOnClickListener { show(i) }
            }
            tabs.add(b)
            bar.addView(b, LinearLayout.LayoutParams(0, dp(if (isTv) 58 else 48), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
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

    private fun createBrandHeader(): View {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(11, 25, 44), Color.rgb(13, 35, 55))
            )
        }
        val mark = TextView(this).apply {
            text = "▶"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(226, 38, 54))
                cornerRadius = dp(15).toFloat()
            }
        }
        header.addView(mark, LinearLayout.LayoutParams(dp(44), dp(44)))
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, 0, 0)
        }
        labels.addView(TextView(this).apply {
            text = "ADFREE  TUBE"
            textSize = 16f
            letterSpacing = 0.08f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        labels.addView(TextView(this).apply {
            text = "Entertainment, made simple"
            textSize = 10f
            setTextColor(Color.rgb(143, 165, 190))
        })
        header.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "●  READY"
            textSize = 9f
            letterSpacing = 0.06f
            setTextColor(Color.rgb(112, 229, 185))
            typeface = Typeface.DEFAULT_BOLD
        })
        return header
    }

    private fun sectionAt(i: Int): Section {
        var s = sections[i]
        if (s == null) {
            s = when (i) {
                0 -> YoutubeSection(this)
                1 -> TvSection(this, isTv) { fullscreen ->
                    tabsBar.visibility = if (fullscreen) View.GONE else View.VISIBLE
                }
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
        content.addView(s.view, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        s.onShow()
        currentIndex = index
        tabs.forEachIndexed { i, b ->
            b.alpha = if (i == index) 1f else 0.78f
            b.background = navBackground(
                if (i == index) "#E32636" else "#17263D", 18
            )
        }
        tabsBar.visibility = View.VISIBLE
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun navBackground(color: String, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.parseColor("#344963"))
        }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val s = if (currentIndex >= 0) sections[currentIndex] else null
        if (s != null && s.onBack()) return
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()
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
