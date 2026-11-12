package com.adfree.tube

import android.app.Activity
import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<Button>()
    private var isTv = false

    private val names = listOf("YouTube", "TV", "Movies")
    private val info = listOf(
        "YouTube section\n\nStage 3 mein yahan ad-free YouTube aayega.",
        "TV section\n\nStage 2 mein yahan Pakistan aur India ke channels aayenge.",
        "Movies section\n\nStage 2 mein yahan movies ka link ad-block ke saath khulega."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uiMode = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTv = uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B1E3F"))
        }

        content = FrameLayout(this)
        val contentParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        )

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val textSize = if (isTv) 24f else 16f
        names.forEachIndexed { i, n ->
            val b = Button(this).apply {
                text = n
                this.textSize = textSize
                isFocusable = true
                setOnClickListener { show(i) }
            }
            tabs.add(b)
            bar.addView(
                b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

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

    private fun show(index: Int) {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = info[index]
            setTextColor(Color.WHITE)
            textSize = if (isTv) 30f else 20f
            gravity = Gravity.CENTER
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        tabs.forEachIndexed { i, b -> b.alpha = if (i == index) 1f else 0.6f }
    }
}
