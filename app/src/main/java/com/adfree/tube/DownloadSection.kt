package com.adfree.tube

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** In-app downloader for direct, openly downloadable media file URLs. */
class DownloadSection(private val activity: Activity) : Section {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val linkInput = EditText(activity)
    private val detected = TextView(activity)
    private val status = TextView(activity)
    private val history = LinearLayout(activity)
    override val view: View

    init {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.rgb(6, 13, 25))
        }
        root.addView(TextView(activity).apply {
            text = "VIDEO DOWNLOADER"
            textSize = 21f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        })
        root.addView(TextView(activity).apply {
            text = "Paste a direct video or audio file link"
            textSize = 12f
            setTextColor(Color.rgb(145, 164, 188))
        }, params(top = 4, bottom = 14))
        linkInput.hint = "https://website.com/video.mp4"
        linkInput.setHintTextColor(Color.rgb(125, 145, 170))
        linkInput.setTextColor(Color.WHITE)
        linkInput.textSize = 14f
        linkInput.setSingleLine(true)
        linkInput.setPadding(dp(13), dp(12), dp(13), dp(12))
        linkInput.background = rounded("#172943", 13)
        root.addView(linkInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        val detect = actionButton("Detect Link")
        detect.setOnClickListener { detectLink() }
        root.addView(detect, params(top = 10, bottom = 8))
        detected.setTextColor(Color.rgb(205, 220, 240))
        detected.textSize = 12f
        detected.setPadding(dp(12), dp(10), dp(12), dp(10))
        detected.background = rounded("#122238", 12)
        detected.text = "Paste a URL and tap Detect Link."
        root.addView(detected, params())
        val download = actionButton("⬇  Download File")
        download.setOnClickListener { startDownload() }
        root.addView(download, params(top = 12, bottom = 8))
        status.setTextColor(Color.rgb(124, 245, 197))
        status.textSize = 12f
        root.addView(status, params(bottom = 14))
        root.addView(TextView(activity).apply {
            text = "DOWNLOADS"
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, params(bottom = 7))
        history.orientation = LinearLayout.VERTICAL
        root.addView(history, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(TextView(activity).apply {
            text = "Note: Platform pages may require login, DRM or protected-stream extraction. Only direct media-file URLs are downloaded. Quality depends on the supplied source."
            textSize = 10f
            setTextColor(Color.rgb(143, 160, 184))
        }, params(top = 8))
        view = root
    }

    private fun detectLink() {
        val uri = validUri() ?: run {
            detected.text = "Please paste a valid http/https link."
            return
        }
        val host = uri.host!!.lowercase()
        val path = uri.path.orEmpty().lowercase()
        val ext = path.substringAfterLast('.', "")
        val type = when (ext) {
            "mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp" -> "Direct video file"
            "mp3", "m4a", "aac", "wav", "ogg", "flac" -> "Direct audio file"
            "m3u8", "mpd" -> "Streaming playlist (not a single file)"
            else -> "Web page / unknown media"
        }
        val platform = when {
            host.contains("youtube.com") || host == "youtu.be" -> "YouTube"
            host.contains("instagram.com") -> "Instagram"
            host.contains("facebook.com") || host == "fb.watch" -> "Facebook"
            host.contains("tiktok.com") -> "TikTok"
            else -> host
        }
        detected.text = "Source: $platform\nType: $type\nQuality: determined by the supplied source"
        status.text = if (type.startsWith("Direct")) "Direct file detected. Ready to download."
            else "This link is not a direct media file. A supported direct file URL is required."
    }

    private fun validUri(): Uri? {
        val uri = runCatching { Uri.parse(linkInput.text.toString().trim()) }.getOrNull() ?: return null
        return uri.takeIf { it.scheme in listOf("http", "https") && !it.host.isNullOrBlank() }
    }

    private fun startDownload() {
        val uri = validUri() ?: run {
            Toast.makeText(activity, "Valid video/audio URL paste karein", Toast.LENGTH_SHORT).show()
            return
        }
        val ext = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        val allowed = setOf("mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp",
            "mp3", "m4a", "aac", "wav", "ogg", "flac")
        if (ext !in allowed) {
            Toast.makeText(activity, "Direct MP4/video/audio file URL chahiye; webpage ya protected stream supported nahi.", Toast.LENGTH_LONG).show()
            detectLink()
            return
        }
        try {
            val name = URLUtil.guessFileName(uri.toString(), null, null)
                .replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "media.$ext" }
            val request = DownloadManager.Request(uri)
                .setTitle(name)
                .setDescription("AdFree Tube download")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_DOWNLOADS, name)
            manager.enqueue(request)
            val row = TextView(activity).apply {
                text = "⬇  $name  •  Queued"
                textSize = 12f
                setTextColor(Color.rgb(220, 233, 248))
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = rounded("#14263D", 10)
            }
            history.addView(row, params(bottom = 6))
            status.text = "Download queued. Android Download Manager is handling the transfer."
            Toast.makeText(activity, "Download started", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            status.text = "Download start nahi hua: " + (e.message ?: "unknown error")
        }
    }

    private fun actionButton(label: String) = Button(activity).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded("#176B50", 13)
        minHeight = dp(46)
        isFocusable = true
    }
    private fun rounded(color: String, radius: Int) = GradientDrawable().apply {
        setColor(Color.parseColor(color))
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.rgb(48, 70, 94))
    }
    private fun params(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (top > 0) topMargin = dp(top)
            if (bottom > 0) bottomMargin = dp(bottom)
        }
    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
