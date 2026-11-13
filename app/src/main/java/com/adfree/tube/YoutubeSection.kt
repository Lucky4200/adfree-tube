package com.adfree.tube

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * YouTube page inside the AdFree Tube shell.
 * The website itself is supplied by YouTube; this native toolbar is app-controlled.
 */
class YoutubeSection(private val activity: Activity) : Section {

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

        val homeMark = TextView(activity).apply {
            text = "▶"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(226, 38, 54))
                cornerRadius = dp(13).toFloat()
            }
        }
        toolbar.addView(homeMark, LinearLayout.LayoutParams(dp(40), dp(40)))

        val titleBlock = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        titleBlock.addView(TextView(activity).apply {
            text = "YouTube"
            textSize = 17f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
        })
        titleBlock.addView(TextView(activity).apply {
            text = "Watch what you love"
            textSize = 10f
            setTextColor(Color.rgb(145, 164, 188))
        })
        toolbar.addView(titleBlock, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val home = makeAction("⌂", "Home")
        home.setOnClickListener { web.loadUrl(Config.YOUTUBE_URL) }
        toolbar.addView(home, LinearLayout.LayoutParams(dp(58), dp(42)).apply {
            marginEnd = dp(7)
        })

        val reload = TextView(activity).apply {
            text = "↻"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = pill("#243850")
            contentDescription = "Reload YouTube"
            isFocusable = true
            setOnClickListener { web.reload() }
        }
        toolbar.addView(reload, LinearLayout.LayoutParams(dp(42), dp(42)))

        web = WebFactory.create(
            activity, chrome,
            { host -> host.isNotBlank() },
            {
                WebFactory.showErrorPage(web, Config.YOUTUBE_URL, "YouTube")
            },
            {},
            keepMediaInBackground = true
        )

        col.addView(toolbar)
        col.addView(View(activity).apply {
            setBackgroundColor(Color.rgb(36, 57, 79))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        col.addView(web, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        view = col
        web.loadUrl(Config.YOUTUBE_URL)
    }

    private fun makeAction(symbol: String, label: String): TextView =
        TextView(activity).apply {
            text = "$symbol  $label"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = pill("#243850")
            isFocusable = true
        }

    private fun pill(color: String): GradientDrawable = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(14).toFloat()
        setStroke(dp(1), Color.rgb(57, 78, 101))
    }

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    override fun onBack(): Boolean {
        // Back should first navigate YouTube's own WebView history.
        // Do not move the whole app to background just because a video is playing.
        if (chrome.hideIfShown()) return true
        if (web.canGoBack()) {
            web.goBack()
            return true
        }
        // At YouTube home, let MainActivity handle the normal app back action.
        return false
    }

    override fun onShow() = web.onResume()
    override fun onHide() = web.onPause()
    override fun onDestroy() = web.destroy()
}
