package com.adfree.tube

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Environment
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlin.concurrent.thread

object DownloadStore {
    const val PREFS = "adfree_tube_downloads"
    const val KEY_IDS = "ids"

    fun enqueue(context: Context, url: String, title: String, mimeType: String? = null): Long {
        val uri = Uri.parse(url)
        val ext = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        val allowed = setOf("mp4", "m4v", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")
        require(uri.scheme in listOf("http", "https") &&
            (ext in allowed || mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true)) {
            "Direct downloadable video/audio file detect nahi hui. HLS/DASH ya protected stream ko direct file ki tarah download nahi kiya ja sakta."
        }
        val name = URLUtil.guessFileName(url, null, mimeType)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "media.${ext.ifBlank { "bin" }}" }
        val request = DownloadManager.Request(uri)
            .setTitle(title.ifBlank { name })
            .setDescription("AdFree Tube • Fast download")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(
                if (mimeType?.startsWith("audio/") == true || ext in setOf("mp3", "m4a", "aac", "wav", "ogg")) Environment.DIRECTORY_MUSIC + "/AdFree Tube"
                else Environment.DIRECTORY_MOVIES + "/AdFree Tube",
                name
            )
        val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(KEY_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(id.toString())
        prefs.edit().putStringSet(KEY_IDS, ids).apply()
        return id
    }

    fun ids(context: Context): List<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_IDS, emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.sortedDescending()
}

class WebDownloadPanel(private val activity: Activity) {
    private var web: WebView? = null
    @Volatile private var mediaUrl = ""
    @Volatile private var pageUrl = ""
    @Volatile private var mediaTitle = "Video"
    @Volatile private var mediaMime = ""
    @Volatile private var width = 0
    @Volatile private var height = 0
    private val handler = Handler(Looper.getMainLooper())
    private val scanTask = object : Runnable {
        override fun run() {
            scan()
            handler.postDelayed(this, 1800)
        }
    }

    private val heading = TextView(activity).apply {
        text = "VIDEO DETECTED"
        textSize = 10f
        setTextColor(Color.rgb(119, 220, 184))
        typeface = Typeface.DEFAULT_BOLD
    }
    private val detail = TextView(activity).apply {
        text = "Play a video on this page to detect its available download options."
        textSize = 11f
        setTextColor(Color.rgb(210, 222, 239))
        maxLines = 3
    }
    private val progress = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        visibility = View.GONE
    }

    val view: View = LinearLayout(activity).apply {
        visibility = View.GONE
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(9), dp(13), dp(9))
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(16, 39, 59), Color.rgb(10, 27, 44))
        ).apply {
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.rgb(44, 83, 104))
        }
        val textColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(heading)
            addView(detail, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(3)
            })
            addView(progress, LinearLayout.LayoutParams(-1, dp(4)).apply {
                topMargin = dp(6)
            })
        }
        addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(activity).apply {
            text = "Download"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(22, 143, 99))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { chooseDownload() }
        }, LinearLayout.LayoutParams(dp(108), dp(42)).apply { marginStart = dp(8) })
    }

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private fun isHttpUrl(value: String): Boolean =
        runCatching { Uri.parse(value).scheme in listOf("http", "https") && !Uri.parse(value).host.isNullOrBlank() }
            .getOrDefault(false)

    private fun extension(value: String): String =
        runCatching { Uri.parse(value).path.orEmpty().substringAfterLast('.', "").lowercase() }.getOrDefault("")

    private fun isDirectFile(value: String): Boolean =
        extension(value) in setOf("mp4", "m4v", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")

    private fun isAdaptiveStream(value: String): Boolean =
        extension(value) in setOf("m3u8", "mpd") ||
            value.contains("mpegurl", ignoreCase = true) ||
            value.contains("dash+xml", ignoreCase = true)

    private val bridge = object {
        @JavascriptInterface
        fun onMedia(url: String?, title: String?, w: Int, h: Int, mime: String?, page: String?) {
            val candidate = url?.takeIf { isHttpUrl(it) } ?: return
            activity.runOnUiThread {
                mediaUrl = candidate
                pageUrl = page?.takeIf { isHttpUrl(it) } ?: web?.url.orEmpty()
                mediaTitle = title?.take(120)?.ifBlank { "Video" } ?: "Video"
                mediaMime = mime.orEmpty()
                width = w
                height = h
                heading.text = if (isAdaptiveStream(candidate)) "STREAM DETECTED • CHECK DOWNLOAD OPTIONS" else "VIDEO DETECTED • DOWNLOAD AVAILABLE"
                detail.text = mediaTitle + " • " + if (w > 0 && h > 0) "${w}×${h}" else "Checking source quality"
                view.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun onUnsupported(title: String?, page: String?, source: String?) {
            activity.runOnUiThread {
                pageUrl = page?.takeIf { isHttpUrl(it) } ?: web?.url.orEmpty()
                mediaUrl = source?.takeIf { isHttpUrl(it) }.orEmpty()
                mediaTitle = title?.take(120)?.ifBlank { "Video" } ?: "Video"
                mediaMime = ""
                heading.text = "PLAYER DETECTED • TRY DOWNLOAD"
                detail.text = mediaTitle + " • Finding the website's available video formats…"
                view.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun onFrame(url: String?, title: String?) {
            val frameUrl = url?.takeIf { isHttpUrl(it) } ?: return
            activity.runOnUiThread {
                pageUrl = frameUrl
                if (mediaUrl.isBlank()) {
                    mediaTitle = title?.take(120)?.ifBlank { "Embedded video" } ?: "Embedded video"
                    heading.text = "EMBEDDED PLAYER DETECTED"
                    detail.text = "Tap Download to check the player for available video and audio formats."
                    view.visibility = View.VISIBLE
                }
            }
        }

        @JavascriptInterface
        fun onNoMedia() {
            activity.runOnUiThread {
                mediaUrl = ""
                pageUrl = ""
                view.visibility = View.GONE
            }
        }
    }

    fun attach(view: WebView) {
        web = view
        view.addJavascriptInterface(bridge, "AdFreeTubeMedia")
        view.setDownloadListener(DownloadListener { url, _, contentDisposition, mimeType, _ ->
            if (url.isNullOrBlank()) return@DownloadListener
            val type = mimeType.orEmpty()
            val ext = extension(url)
            if (type.startsWith("video/") || type.startsWith("audio/") ||
                ext in setOf("mp4", "m4v", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg")) {
                mediaUrl = url
                pageUrl = view.url.orEmpty()
                mediaTitle = URLUtil.guessFileName(url, contentDisposition, type).ifBlank { "Video" }
                mediaMime = type
                activity.runOnUiThread {
                    heading.text = "DOWNLOAD LINK DETECTED"
                    detail.text = mediaTitle + " • Direct media file"
                    this.view.visibility = View.VISIBLE
                    chooseDownload()
                }
            } else {
                Toast.makeText(activity, "This link is not a direct downloadable media file.", Toast.LENGTH_LONG).show()
            }
        })
        resume()
    }

    fun resume() {
        handler.removeCallbacks(scanTask)
        handler.post(scanTask)
    }

    fun pause() {
        handler.removeCallbacks(scanTask)
    }

    private fun scan() {
        val target = web ?: return
        if (!target.isAttachedToWindow) return
        target.evaluateJavascript(
            """(function(){
              try {
                var page = window.location.href || '';
                var title = document.title || 'Video';
                var videos = Array.prototype.slice.call(document.querySelectorAll('video'));
                var video = videos.find(function(x){ return !x.paused && !x.ended; }) || videos[videos.length - 1];
                if (video) {
                  var sources = Array.prototype.slice.call(video.querySelectorAll('source')).map(function(s){ return s.src || ''; });
                  var candidate = video.currentSrc || video.src || sources.find(function(s){ return s; }) || '';
                  var mime = video.getAttribute('type') || (video.querySelector('source') && video.querySelector('source').type) || '';
                  var w = video.videoWidth || 0, h = video.videoHeight || 0;
                  var direct = /^https?:\/\//i.test(candidate) && /\.(mp4|m4v|webm|mov|mp3|m4a|aac|wav|ogg|opus|flac|m3u8|mpd)([?#]|$)/i.test(candidate);
                  if (direct) AdFreeTubeMedia.onMedia(candidate, title, w, h, mime, page);
                  else AdFreeTubeMedia.onUnsupported(title, page, candidate);
                  return;
                }
                var frame = Array.prototype.slice.call(document.querySelectorAll('iframe'))
                  .map(function(f){ return f.src || f.getAttribute('src') || ''; })
                  .find(function(s){ return /^https?:\/\//i.test(s); });
                if (frame) AdFreeTubeMedia.onFrame(frame, title);
                else AdFreeTubeMedia.onNoMedia();
              } catch(e) {}
            })();""",
            null
        )
    }

    private fun chooseDownload() {
        val currentPage = pageUrl.takeIf { isHttpUrl(it) } ?: web?.url.orEmpty()
        val directSource = mediaUrl.takeIf { isHttpUrl(it) }.orEmpty()
        val targets = listOf(currentPage, directSource).filter { isHttpUrl(it) }.distinct()
        if (targets.isEmpty()) {
            Toast.makeText(activity, "Player link abhi detect nahi hua. Video dobara play karein.", Toast.LENGTH_LONG).show()
            return
        }

        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        detail.text = "Checking website formats…"
        thread {
            var found: VideoInfo? = null
            var foundAt = ""
            var lastError = ""
            for (target in targets) {
                try {
                    found = YtDlpEngine.extract(target)
                    foundAt = target
                    break
                } catch (e: Exception) {
                    lastError = e.message ?: "Format extraction failed"
                }
            }
            activity.runOnUiThread {
                progress.isIndeterminate = false
                progress.visibility = View.GONE
                if (found != null) {
                    showFormatOptions(found!!, foundAt)
                } else {
                    showDirectFallback(directSource, lastError)
                }
            }
        }
    }

    private fun showFormatOptions(video: VideoInfo, sourceUrl: String) {
        val heights = video.formats.orEmpty()
            .map { it.height }
            .filter { it in 1..4320 }
            .distinct()
            .sortedDescending()
        val options = mutableListOf("Best available video • up to 8K", "MP4 compatible")
        options.addAll(heights.map { if (it == 4320) "4320p • 8K" else "${it}p" })
        options.add("Audio only • MP3")
        val top = heights.firstOrNull()
        val summary = buildString {
            append(video.title ?: mediaTitle)
            append("\nAvailable video: ")
            append(if (top != null) (if (top >= 4320) "up to 8K (4320p)" else "${top}p") else "source quality")
            append("\nChoose a format to start downloading.")
        }
        AlertDialog.Builder(activity)
            .setTitle("Download options")
            .setMessage(summary)
            .setItems(options.toTypedArray()) { _, which ->
                val selected = options[which]
                val audioOnly = selected.startsWith("Audio only")
                val selector = when {
                    audioOnly -> "bestaudio/best"
                    selected == "Best available video • up to 8K" ->
                        "bestvideo[height<=4320]+bestaudio/best[height<=4320]"
                    selected == "MP4 compatible" ->
                        "bestvideo[ext=mp4][height<=4320]+bestaudio[ext=m4a]/best[ext=mp4][height<=4320]/best[height<=4320]"
                    else -> {
                        val limit = selected.substringBefore("p").toIntOrNull() ?: 720
                        "bestvideo[height<=$limit]+bestaudio/best[height<=$limit]"
                    }
                }
                startYtDlpDownload(sourceUrl, selector, audioOnly, selected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startYtDlpDownload(sourceUrl: String, selector: String, audioOnly: Boolean, label: String) {
        heading.text = "DOWNLOAD IN PROGRESS"
        detail.text = "Starting: $label"
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        thread {
            try {
                val result = YtDlpEngine.download(activity, sourceUrl, selector, audioOnly) { percent, eta, line ->
                    activity.runOnUiThread {
                        progress.isIndeterminate = false
                        progress.progress = percent.toInt().coerceIn(0, 100)
                        detail.text = if (line.isNotBlank()) line.takeLast(220) else
                            "Downloading ${percent.toInt().coerceIn(0, 100)}% • ETA ${eta}s"
                    }
                }
                activity.runOnUiThread {
                    progress.visibility = View.GONE
                    heading.text = "DOWNLOAD COMPLETE"
                    detail.text = "✓ $result • Saved to Gallery / Music"
                    Toast.makeText(activity, "Download complete • check Gallery", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    progress.visibility = View.GONE
                    heading.text = "DOWNLOAD FAILED"
                    detail.text = e.message?.take(240) ?: "Download failed"
                    Toast.makeText(activity, detail.text, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDirectFallback(source: String, error: String) {
        val ext = extension(source)
        if (isDirectFile(source)) {
            val isAudio = ext in setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")
            AlertDialog.Builder(activity)
                .setTitle("Direct media available")
                .setMessage("This website gives one direct file, so only its original quality is available here. The file will be saved in Gallery / Music.")
                .setPositiveButton("Download original") { _, _ ->
                    try {
                        DownloadStore.enqueue(activity, source, mediaTitle, mediaMime.ifBlank {
                            if (isAudio) "audio/$ext" else "video/$ext"
                        })
                        heading.text = "DOWNLOAD STARTED"
                        detail.text = "Original file is downloading. Check the Downloads tab."
                        Toast.makeText(activity, "Download started", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        detail.text = e.message ?: "Download failed"
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        if (isAdaptiveStream(source)) {
            AlertDialog.Builder(activity)
                .setTitle("Streaming formats")
                .setMessage("The site exposes an HLS/DASH stream. Choose an export format. Protected / DRM streams are not bypassed.")
                .setItems(arrayOf("Video • MP4", "Audio only • M4A")) { _, which ->
                    Media3Export.start(activity, source, audioOnly = which == 1) { status ->
                        activity.runOnUiThread {
                            detail.text = status
                            if (status.startsWith("Complete")) heading.text = "DOWNLOAD COMPLETE"
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("This player is not downloadable")
            .setMessage("The page did not expose an extractable video file. It may use DRM, a protected stream, or a player that this app does not support. Protected streams cannot be bypassed.\n\n" + error.take(260))
            .setPositiveButton("OK", null)
            .show()
    }
}
