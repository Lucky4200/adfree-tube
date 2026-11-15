package com.adfree.tube

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper

class DownloadSection(private val activity: Activity) : Section {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val linkInput = EditText(activity)
    private val detected = TextView(activity)
    private val status = TextView(activity)
    private val history = LinearLayout(activity)
    private val handler = Handler(Looper.getMainLooper())
    override val view: View

    private val refreshTask = object : Runnable {
        override fun run() {
            refreshDownloads()
            handler.postDelayed(this, 1200)
        }
    }

    init {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.rgb(6, 13, 25))
        }
        root.addView(label("VIDEO DOWNLOADER", 21, true))
        root.addView(label("Paste a direct video or audio file link", 12).apply {
            setTextColor(Color.rgb(145, 164, 188))
        }, params(top = 4, bottom = 14))
        linkInput.hint = "https://website.com/video.mp4"
        linkInput.setHintTextColor(Color.rgb(125, 145, 170))
        linkInput.setTextColor(Color.WHITE)
        linkInput.textSize = 14f
        linkInput.setSingleLine(true)
        linkInput.setPadding(dp(13), dp(12), dp(13), dp(12))
        linkInput.background = rounded("#172943", 13)
        root.addView(linkInput, LinearLayout.LayoutParams(-1, dp(50)))
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
        root.addView(label("DOWNLOADS • LIVE PROGRESS", 14, true), params(bottom = 7))
        history.orientation = LinearLayout.VERTICAL
        root.addView(history, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(label("Only direct, openly downloadable media files are supported. Protected or DRM streams are not extracted.", 10).apply {
            setTextColor(Color.rgb(143, 160, 184))
        }, params(top = 8))
        view = root
    }

    private fun detectLink() {
        val uri = validUri() ?: run { detected.text = "Please paste a valid http/https link."; return }
        val host = uri.host!!.lowercase()
        val ext = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        val type = when (ext) {
            "mp4", "m4v", "mkv", "webm", "mov", "avi", "3gp" -> "Direct video file"
            "mp3", "m4a", "aac", "wav", "ogg", "flac" -> "Direct audio file"
            "m3u8", "mpd" -> "Streaming playlist"
            else -> "Web page / unknown media"
        }
        val platform = when {
            host.contains("youtube.com") || host == "youtu.be" -> "YouTube"
            host.contains("instagram.com") -> "Instagram"
            host.contains("facebook.com") || host == "fb.watch" -> "Facebook"
            host.contains("tiktok.com") -> "TikTok"
            else -> host
        }
        detected.text = "Source: $platform\nType: $type\nQuality: original source"
        status.text = if (type.startsWith("Direct")) "Direct file detected. Ready to download."
            else "A direct media-file URL is required."
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
        try {
            DownloadStore.enqueue(activity, uri.toString(), URLUtil.guessFileName(uri.toString(), null, null))
            status.text = "Download queued. Live progress neeche show hoga."
            refreshDownloads()
            Toast.makeText(activity, "Download added", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            status.text = e.message ?: "Download start nahi hua."
        }
    }

    private fun refreshDownloads() {
        history.removeAllViews()
        val ids = DownloadStore.ids(activity)
        if (ids.isEmpty()) {
            history.addView(label("Abhi koi download nahi hai.", 12).apply {
                setTextColor(Color.rgb(145, 164, 188))
            })
            return
        }
        ids.forEach { id ->
            val query = DownloadManager.Query().setFilterById(id)
            val cursor = runCatching { manager.query(query) }.getOrNull()
            if (cursor == null) return@forEach
            cursor.use {
                if (!it.moveToFirst()) return@use
                val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Media"
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val code = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val percent = if (total > 0) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
                val state = when (code) {
                    DownloadManager.STATUS_PENDING -> "Waiting"
                    DownloadManager.STATUS_RUNNING -> "Downloading • $percent%"
                    DownloadManager.STATUS_PAUSED -> "Paused"
                    DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                    DownloadManager.STATUS_FAILED -> "Failed"
                    else -> "Queued"
                }
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                    background = rounded("#14263D", 10)
                }
                row.addView(label(title, 12, true))
                row.addView(label("$state  •  ${formatBytes(downloaded)} / ${if (total > 0) formatBytes(total) else "unknown"}", 10).apply {
                    setTextColor(Color.rgb(166, 190, 216))
                }, params(top = 4))
                row.addView(ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = percent
                }, LinearLayout.LayoutParams(-1, dp(5)).apply { topMargin = dp(7) })
                history.addView(row, params(bottom = 7))
            }
        }
    }

    private fun formatBytes(n: Long): String = when {
        n < 0 -> "0 B"
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> String.format("%.1f KB", n / 1024.0)
        n < 1024L * 1024L * 1024L -> String.format("%.1f MB", n / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", n / (1024.0 * 1024.0 * 1024.0))
    }

    private fun label(text: String, size: Int, bold: Boolean = false) = TextView(activity).apply {
        this.text = text
        textSize = size.toFloat()
        setTextColor(Color.WHITE)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun actionButton(textValue: String) = Button(activity).apply {
        text = textValue
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
        LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (top > 0) topMargin = dp(top)
            if (bottom > 0) bottomMargin = dp(bottom)
        }
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    override fun onShow() {
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }
    override fun onHide() { handler.removeCallbacks(refreshTask) }
    override fun onDestroy() { handler.removeCallbacksAndMessages(null) }
}
