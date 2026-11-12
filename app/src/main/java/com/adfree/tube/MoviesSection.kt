package com.adfree.tube

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MoviesSection(private val activity: Activity) : Section {

    private val chrome = FullscreenChrome(activity)
    private lateinit var web: WebView
    override val view: View

    init {
        val col = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(6, 13, 25))
        }
        val toolbar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(9))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(13, 29, 49), Color.rgb(10, 21, 37))
            )
        }
        val mark = TextView(activity).apply {
            text = "▰"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded("#6B55D9")
        }
        toolbar.addView(mark, LinearLayout.LayoutParams(dp(40), dp(40)))
        val titleBlock = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titleBlock.addView(TextView(activity).apply {
            text = "Movies"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
        })
        titleBlock.addView(TextView(activity).apply {
            text = "Explore and enjoy"
            textSize = 10f
            setTextColor(Color.rgb(145, 164, 188))
        })
        toolbar.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val home = action("⌂  Home")
        home.setOnClickListener { web.loadUrl(Config.MOVIES_URL) }
        toolbar.addView(home, LinearLayout.LayoutParams(dp(72), dp(42)).apply { marginEnd = dp(7) })
        val reload = action("↻")
        reload.textSize = 22f
        reload.setOnClickListener { web.reload() }
        toolbar.addView(reload, LinearLayout.LayoutParams(dp(42), dp(42)))

        web = WebFactory.create(
            activity, chrome,
            { host -> host.isNotBlank() },
            {
                Toast.makeText(activity, "Movies ka link nahi khula. Internet check karein.", Toast.LENGTH_LONG).show()
            },
            {}
        )
        col.addView(toolbar)
        col.addView(View(activity).apply { setBackgroundColor(Color.rgb(36, 57, 79)) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        col.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        view = col
        web.loadUrl(Config.MOVIES_URL)
    }

    private fun action(label: String): TextView = TextView(activity).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        background = rounded("#243850")
        isFocusable = true
    }

    private fun rounded(color: String): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), Color.rgb(57, 78, 101))
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

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
