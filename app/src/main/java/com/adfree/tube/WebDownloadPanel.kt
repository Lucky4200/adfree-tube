package com.adfree.tube

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.DownloadListener
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object DownloadStore {
    const val PREFS = "adfree_tube_downloads"
    const val KEY_IDS = "ids"

    fun enqueue(context: Context, url: String, title: String, mimeType: String? = null): Long {
        val uri = Uri.parse(url)
        val ext = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        val allowed = setOf("mp4", "m4v", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg")
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
    @Volatile private var mediaTitle = "Video"
    @Volatile private var mediaMime = ""
    @Volatile private var width = 0
    @Volatile private var height = 0
    private val handler = Handler(Looper.getMainLooper())
    private val scanTask = object : Runnable {
        override fun run() { scan(); handler.postDelayed(this, 2200) }
    }

    private val heading = TextView(activity).apply {
        text = "VIDEO DETECTED"
        textSize = 10f
        setTextColor(Color.rgb(119, 220, 184))
        typeface = Typeface.DEFAULT_BOLD
    }
    private val detail = TextView(activity).apply {
        text = "Play a video on this page to check for a downloadable source."
        textSize = 11f
        setTextColor(Color.rgb(210, 222, 239))
        maxLines = 2
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

    private val bridge = object {
        @JavascriptInterface
        fun onMedia(url: String?, title: String?, w: Int, h: Int, mime: String?) {
            val candidate = url?.takeIf { it.isNotBlank() } ?: return
            activity.runOnUiThread {
                mediaUrl = candidate
                mediaTitle = title?.take(120)?.ifBlank { "Video" } ?: "Video"
                mediaMime = mime.orEmpty()
                width = w
                height = h
                heading.text = "VIDEO DETECTED • DOWNLOAD AVAILABLE"
                detail.text = mediaTitle + " • " + if (w > 0 && h > 0) "${w}×${h}" else "Original source quality"
                view.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun onUnsupported(title: String?) {
            activity.runOnUiThread {
                mediaUrl = ""
                mediaTitle = title?.take(120)?.ifBlank { "Video" } ?: "Video"
                heading.text = "VIDEO PLAYING"
                detail.text = "This player uses a segmented, protected or non-direct stream. A direct file is not available."
                view.visibility = View.VISIBLE
            }
        }

        @JavascriptInterface
        fun onNoMedia() {
            activity.runOnUiThread {
                mediaUrl = ""
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
            val ext = Uri.parse(url).path.orEmpty().substringAfterLast('.', "").lowercase()
            if (type.startsWith("video/") || type.startsWith("audio/") ||
                ext in setOf("mp4", "m4v", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg")) {
                mediaUrl = url
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
    fun pause() { handler.removeCallbacks(scanTask) }

    private fun scan() {
        val target = web ?: return
        if (!target.isAttachedToWindow) return
        target.evaluateJavascript(
            """(function(){
              try {
                var videos=[].slice.call(document.querySelectorAll('video'));
                var v=videos.find(function(x){return !x.paused && !x.ended;});
                if(!v){AdFreeTubeMedia.onNoMedia();return;}
                var u=v.currentSrc||v.src||'';
                var title=document.title||'Video';
                var w=v.videoWidth||0,h=v.videoHeight||0;
                var sources=[].slice.call(v.querySelectorAll('source')).map(function(s){return s.src||'';});
                var candidate=u||sources.find(function(s){return s;})||'';
                var direct=/\.(mp4|m4v|webm|mov|mp3|m4a|aac|wav|ogg)(\?|#|$)/i.test(candidate);
                if(direct && /^https?:/i.test(candidate)){
                  AdFreeTubeMedia.onMedia(candidate,title,w,h,v.getAttribute('type')||'');
                } else {
                  AdFreeTubeMedia.onUnsupported(title);
                }
              } catch(e) {}
            })();""", null
        )
    }

    private fun chooseDownload() {
        val url = mediaUrl
        if (url.isBlank()) {
            Toast.makeText(activity, "This player does not expose a direct downloadable media file.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val ext = uri?.path.orEmpty().substringAfterLast('.', "").lowercase()
        val videoExt = setOf("mp4", "m4v", "webm", "mov")
        val audioExt = setOf("mp3", "m4a", "aac", "wav", "ogg")
        if (uri == null || ext !in videoExt + audioExt) {
            AlertDialog.Builder(activity)
                .setTitle("Download not available")
                .setMessage("The player exposes a segmented, protected, or browser-only stream rather than a direct media file. This version cannot safely convert that stream.")
                .setPositiveButton("OK", null).show()
            return
        }
        val quality = if (width > 0 && height > 0) "${width}×${height}" else "Original source quality"
        val format = if (ext in videoExt) "MP4 / original video file" else "Audio / original audio file"
        AlertDialog.Builder(activity)
            .setTitle("Download options")
            .setMessage("Title: $mediaTitle\nFormat: $format\nQuality: $quality\n\nOnly the quality supplied by this website is available here. MP3 conversion and alternate resolutions require a separate transcoding/stream-selection engine.")
            .setPositiveButton("Download original") { _, _ ->
                try {
                    DownloadStore.enqueue(activity, url, mediaTitle, mediaMime)
                    Toast.makeText(activity, "Download started • check Downloads tab", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(activity, e.message ?: "Download failed", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
