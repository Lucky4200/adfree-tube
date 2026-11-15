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
import android.webkit.URLUtil
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object DownloadStore {
    const val PREFS = "adfree_tube_downloads"
    const val KEY_IDS = "ids"

    fun enqueue(context: Context, url: String, title: String): Long {
        val uri = Uri.parse(url)
        val ext = uri.path.orEmpty().substringAfterLast('.', "").lowercase()
        val allowed = setOf("mp4", "m4v", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg")
        require(uri.scheme in listOf("http", "https") && ext in allowed) {
            "This source is not a direct downloadable media file."
        }
        val name = URLUtil.guessFileName(url, null, null)
            .replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "media.$ext" }
        val request = DownloadManager.Request(uri)
            .setTitle(title.ifBlank { name })
            .setDescription("AdFree Tube")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
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
    private var web: android.webkit.WebView? = null
    private var mediaUrl = ""
    private var mediaTitle = "Video"
    private var width = 0
    private var height = 0
    private val handler = Handler(Looper.getMainLooper())
    private val scanTask = object : Runnable { override fun run() { scan() } }

    val view: View = LinearLayout(activity).apply {
        visibility = View.GONE
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(7), dp(12), dp(7))
        setBackgroundColor(Color.rgb(12, 27, 45))
        addView(TextView(activity).apply {
            text = "⬇"
            textSize = 21f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(dp(34), dp(42)))
        addView(TextView(activity).apply {
            text = "Video download"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(activity).apply {
            text = "Download"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.rgb(23, 107, 80))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { chooseDownload() }
        }, LinearLayout.LayoutParams(dp(112), dp(42)))
    }

    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    private val bridge = object {
        @JavascriptInterface
        fun onMedia(url: String?, title: String?, w: Int, h: Int, mime: String?) {
            val candidate = url?.takeIf { it.isNotBlank() } ?: return
            activity.runOnUiThread {
                mediaUrl = candidate
                mediaTitle = title?.take(120)?.ifBlank { "Video" } ?: "Video"
                width = w
                height = h
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

    fun attach(view: android.webkit.WebView) {
        web = view
        view.addJavascriptInterface(bridge, "AdFreeTubeMedia")
        resume()
    }

    fun resume() { handler.removeCallbacks(scanTask); handler.postDelayed(scanTask, 1200) }
    fun pause() { handler.removeCallbacks(scanTask) }

    private fun scan() {
        val target = web ?: return
        if (!target.isAttachedToWindow) return
        target.evaluateJavascript(
            """(function(){
              try {
                var v=document.querySelector('video');
                if(!v || v.paused || v.ended) { AdFreeTubeMedia.onNoMedia(); return; }
                var u=v.currentSrc||v.src||'';
                if(!u) { AdFreeTubeMedia.onNoMedia(); return; }
                AdFreeTubeMedia.onMedia(u,document.title||'Video',v.videoWidth||0,v.videoHeight||0,v.currentSrc||'');
              } catch(e) {}
            })();""", null
        )
        handler.postDelayed(scanTask, 2500)
    }

    private fun chooseDownload() {
        val url = mediaUrl
        if (url.isBlank()) {
            Toast.makeText(activity, "Pehle video play karein. Direct media source detect nahi hua.", Toast.LENGTH_LONG).show()
            return
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val ext = uri?.path.orEmpty().substringAfterLast('.', "").lowercase()
        val supported = setOf("mp4", "m4v", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg")
        if (uri == null || ext !in supported) {
            AlertDialog.Builder(activity)
                .setTitle("Download unavailable")
                .setMessage("Is player ka source segmented, protected, ya direct media file nahi hai. App protected streams ko extract nahi karti.")
                .setPositiveButton("OK", null).show()
            return
        }
        val quality = if (width > 0 && height > 0) "${width}×${height}" else "Original source quality"
        val kind = if (ext in setOf("mp3", "m4a", "aac", "wav", "ogg")) "Audio file" else "Video file"
        AlertDialog.Builder(activity)
            .setTitle("Download options")
            .setItems(arrayOf("$kind • $quality", "Cancel")) { dialog, which ->
                if (which == 0) {
                    try {
                        DownloadStore.enqueue(activity, url, mediaTitle)
                        Toast.makeText(activity, "Download added to Downloads tab", Toast.LENGTH_LONG).show()
                    } catch (e: Exception) {
                        Toast.makeText(activity, e.message ?: "Download failed", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.dismiss()
            }.show()
    }
}
