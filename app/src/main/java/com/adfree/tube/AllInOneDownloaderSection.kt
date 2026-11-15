package com.adfree.tube

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.yausername.youtubedl_android.mapper.VideoInfo
import org.json.JSONObject
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AllInOneDownloaderSection(private val activity: Activity) : Section {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var pendingUrl = ""
    private val clipboardCard = TextView(activity).apply {
        text = "📋 TAP TO AUTO-PASTE & DETECT\nCopy a video link in YouTube, Facebook, Instagram or TikTok, then tap here."
        textSize = 11f
        setTextColor(Color.WHITE)
        gravity = android.view.Gravity.CENTER
        maxLines = 3
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.rgb(23, 41, 67))
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Color.rgb(75, 113, 145))
        }
        isClickable = true
        isFocusable = true
        contentDescription = "Paste copied video link from clipboard and detect it"
        setOnClickListener { pasteClipboardAndDetect() }
    }
    private val info = TextView(activity)
    private val active = LinearLayout(activity)
    private val scanProgress = ProgressBar(activity).apply { isIndeterminate = true; visibility = View.GONE }
    private val scanStatus = TextView(activity).apply { text = "Ready to scan"; textSize = 11f; setTextColor(Color.rgb(145,164,188)) }
    private var mediaUrl = ""
    private var mime = ""
    private var detectedInfo: VideoInfo? = null
    private val handler = Handler(Looper.getMainLooper())
    override val view: View
    private val task = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1500) }
    }
    init {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14),dp(12),dp(14),dp(10))
            setBackgroundColor(Color.rgb(6,13,25))
        }
        root.addView(label("ALL-IN-ONE DOWNLOADER",19,true))
        root.addView(label("Video/audio link Copy karein, phir neeche clipboard card par tap karein.",11).apply {
            setTextColor(Color.rgb(145,164,188))
        },params(5,10))
        val platforms = LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER }
        listOf("f  Facebook","◎  Instagram","♪  TikTok","▶  YouTube","X").forEach { item ->
            platforms.addView(TextView(activity).apply { text=item; textSize=8f; gravity=android.view.Gravity.CENTER; setTextColor(Color.WHITE); setPadding(dp(3),dp(7),dp(3),dp(7)); background=GradientDrawable().apply { setColor(Color.rgb(24,43,67)); cornerRadius=dp(9).toFloat(); setStroke(dp(1),Color.rgb(55,82,112)) } }, LinearLayout.LayoutParams(0,dp(32),1f).apply { marginStart=dp(2); marginEnd=dp(2) })
        }
        root.addView(platforms,params(0,8))
        root.addView(clipboardCard, params(8, 5))
        val scanStatusRow=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL
            addView(scanProgress,LinearLayout.LayoutParams(dp(18),dp(18)))
            addView(scanStatus,LinearLayout.LayoutParams(-2,-2).apply { marginStart=dp(7) })
        }
        root.addView(scanStatusRow,params(1,7))
        info.text="Link abhi detect nahi hua."
        info.textSize=12f; info.setTextColor(Color.rgb(205,220,240))
        info.setPadding(dp(10),dp(10),dp(10),dp(10)); info.background=rounded()
        root.addView(info)
        val start=button("⬇ Start Download")
        start.setOnClickListener { startDownload() }
        root.addView(start,params(8,8))
        root.addView(label("ACTIVE DOWNLOADS",13,true),params(0,6))
        active.orientation=LinearLayout.VERTICAL
        root.addView(active,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(label("Web page links (YouTube / social sites) may not expose a direct file. DRM ya protected streams extract nahi kiye jate.",10).apply {
            setTextColor(Color.rgb(143,160,184))
        },params(6,0))
        view=root
    }
    private fun pasteClipboardAndDetect() {
        val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = runCatching {
            if (clipboard.hasPrimaryClip() && clipboard.primaryClip!!.itemCount > 0)
                clipboard.primaryClip!!.getItemAt(0).coerceToText(activity)?.toString()?.trim().orEmpty()
            else ""
        }.getOrDefault("")
        val link = copied.lineSequence().firstOrNull {
            it.startsWith("https://", true) || it.startsWith("http://", true) || it.startsWith("www.", true)
        }.orEmpty()
        if (link.isBlank()) {
            Toast.makeText(activity, "Pehle doosri app mein video link Copy karein, phir yahan tap karein.", Toast.LENGTH_LONG).show()
            scanStatus.text = "Clipboard mein link nahi mila"
            clipboardCard.text = "📋 LINK NOT FOUND\nCopy a video link, then tap here again."
            return
        }
        pendingUrl = link
        clipboardCard.text = "✓ LINK COPIED AUTOMATICALLY\n" + link.take(150)
        detect()
    }

    private fun detect() {
        val raw = pendingUrl.trim()
        val normalized = if (raw.startsWith("www.", true) || raw.startsWith("youtu.be/", true) || raw.startsWith("youtube.com/", true)) "https://" + raw else raw
        val u = runCatching { Uri.parse(normalized) }.getOrNull()
        if (u == null || u.scheme !in listOf("http", "https") || u.host.isNullOrBlank()) {
            info.text = "Valid http/https link paste karein."
            return
        }
        mediaUrl = ""; mime = ""; detectedInfo = null
        info.text = "Website se video aur available formats scan ho rahe hain..."
        scanProgress.visibility = View.VISIBLE
        scanStatus.text = "Extractor starting..."
        thread {
            try {
                val video = YtDlpEngine.extract(normalized)
                val heights = video.formats.orEmpty().map { it.height }.filter { it in 1..4320 }.distinct().sortedDescending()
                activity.runOnUiThread {
                    detectedInfo = video
                    mediaUrl = normalized
                    mime = "extractor"
                    scanProgress.visibility = View.GONE
                    scanStatus.text = "Video detected • " + video.formats.orEmpty().size + " formats"
                    val title = video.title ?: "Media detected"
                    val creator = if (video.uploader.isNullOrBlank()) "" else "\nBy: " + video.uploader
                    val duration = if (video.duration > 0) "\nDuration: " + (video.duration / 60) + "m " + (video.duration % 60) + "s" else ""
                    val quality = if (heights.isNotEmpty()) "\nAvailable: " + heights.joinToString(", ") { it.toString() + "p" } else "\nAvailable formats: " + video.formats.orEmpty().size
                    info.text = "✓ " + title + creator + duration + quality + "\n\nStart Download dabayein aur quality select karein."
                }
            } catch (extractError: Exception) {
                try {
                    var connection: HttpURLConnection? = null
                    val finalUrl: String
                    val type: String
                    try {
                        connection = URL(normalized).openConnection() as HttpURLConnection
                        connection.requestMethod = "HEAD"; connection.connectTimeout = 10000; connection.readTimeout = 10000
                        connection.instanceFollowRedirects = true; connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                        connection.responseCode
                        finalUrl = connection.url.toString()
                        type = connection.contentType?.substringBefore(";")?.lowercase().orEmpty()
                    } finally { connection?.disconnect() }
                    val ext = Uri.parse(finalUrl).path.orEmpty().substringAfterLast(".", "").lowercase()
                    val direct = type.startsWith("video/") || type.startsWith("audio/") ||
                        ext in setOf("mp4","m4v","webm","mov","mp3","m4a","aac","wav","ogg","m3u8","mpd")
                    if (!direct) throw extractError
                    activity.runOnUiThread {
                        mediaUrl = finalUrl; mime = type; detectedInfo = null
                        scanProgress.visibility = View.GONE
                        scanStatus.text = "Direct media detected"
                        info.text = "Direct media link detect ho gaya ✓\nType: " + type + "\nDownload ke liye ready."
                    }
                } catch (fallbackError: Exception) {
                    activity.runOnUiThread {
                        scanProgress.visibility = View.GONE
                        scanStatus.text = "Link extraction failed"
                        info.text = "Is website se media details nahi mil sakin.\n" + (extractError.message ?: "Extractor error")
                    }
                }
            }
        }
    }
    @OptIn(UnstableApi::class)
    private fun startDownload() {
        val url = mediaUrl
        if (url.isBlank()) {
            Toast.makeText(activity, "Pehle link detect karein.", Toast.LENGTH_LONG).show()
            return
        }
        val extracted = detectedInfo
        if (extracted != null) {
            val heights = extracted.formats.orEmpty().map { it.height }.filter { it in 1..4320 }.distinct().sortedDescending()
            val options = mutableListOf("Best available quality • up to 8K", "MP4 compatible")
            options.addAll(heights.map { if (it == 4320) "4320p • 8K" else it.toString() + "p" })
            options.add("Audio only • MP3")
            AlertDialog.Builder(activity).setTitle(extracted.title ?: "Choose quality")
                .setItems(options.toTypedArray()) { _, which ->
                    val selected = options[which]
                    val audioOnly = selected.startsWith("Audio only")
                    val selector = when {
                        audioOnly -> "bestaudio/best"
                        selected == "Best available quality • up to 8K" ->
                            "bestvideo[height<=4320]+bestaudio/best[height<=4320]"
                        selected == "MP4 compatible" ->
                            "bestvideo[ext=mp4][height<=4320]+bestaudio[ext=m4a]/best[ext=mp4][height<=4320]/best[height<=4320]"
                        else -> {
                            val height = selected.substringBefore("p").toIntOrNull() ?: 720
                            "bestvideo[height<=" + height + "]+bestaudio/best[height<=" + height + "]"
                        }
                    }
                    mediaUrl = ""
                    scanProgress.visibility = View.VISIBLE
                    scanStatus.text = "Download starting..."
                    info.text = "Downloading: " + selected
                    thread {
                        try {
                            val result = YtDlpEngine.download(activity, url, selector, audioOnly) { progress, eta, line ->
                                activity.runOnUiThread {
                                    scanStatus.text = "Downloading " + progress.toInt().coerceIn(0,100) + "% • ETA " + eta + "s"
                                    if (line.isNotBlank()) info.text = line.takeLast(500)
                                }
                            }
                            activity.runOnUiThread {
                                scanProgress.visibility = View.GONE
                                scanStatus.text = "Download complete"
                                info.text = "✓ Download complete\n" + result
                                refresh()
                            }
                        } catch (e: Exception) {
                            activity.runOnUiThread {
                                scanProgress.visibility = View.GONE
                                scanStatus.text = "Download failed"
                                info.text = "Download failed: " + (e.message ?: "Unknown error")
                            }
                        }
                    }
                }.setNegativeButton("Cancel", null).show()
            return
        }
        val ext = Uri.parse(url).path.orEmpty().substringAfterLast(".", "").lowercase()
        val adaptive = mime.contains("mpegurl") || mime.contains("dash+xml") || ext in setOf("m3u8", "mpd")
        val video = mime.startsWith("video/") || ext in setOf("mp4", "m4v", "webm", "mov")
        val audio = mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "wav", "ogg")
        if (adaptive || video) {
            val options = if (adaptive) arrayOf("Video • MP4", "Audio only • M4A") else arrayOf("Video • original file", "Audio only • M4A")
            AlertDialog.Builder(activity).setTitle("Download format").setItems(options) { _, which ->
                if (adaptive || which == 1) {
                    Media3Export.start(activity, url, audioOnly = which == 1) { status ->
                        scanStatus.text = status
                        if (status.startsWith("Complete")) info.text = status
                    }
                    mediaUrl = ""
                } else {
                    try {
                        DownloadStore.enqueue(activity, url, "Media download", mime)
                        info.text = "Download shuru ho gaya. Downloads tab mein status check karein."
                        mediaUrl = ""; refresh()
                    } catch (e: Exception) { info.text = e.message ?: "Download start nahi hua." }
                }
            }.setNegativeButton("Cancel", null).show()
            return
        }
        if (audio) {
            try {
                DownloadStore.enqueue(activity, url, "Audio download", mime)
                info.text = "Audio download shuru ho gaya. Downloads tab mein status check karein."
                mediaUrl = ""; refresh()
            } catch (e: Exception) { info.text = e.message ?: "Download start nahi hua." }
            return
        }
        info.text = "Is link ka downloadable media format identify nahi hua."
    }
    private fun refresh() {
        active.removeAllViews()
        var n=0
        DownloadStore.ids(activity).forEach { id ->
            val cur=runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull() ?: return@forEach
            cur.use {
                if(!it.moveToFirst()) return@use
                val state=it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if(state==DownloadManager.STATUS_SUCCESSFUL || state==DownloadManager.STATUS_FAILED) return@use
                n++
                val title=it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Media"
                val got=it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total=it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val pct=if(total>0)(got*100L/total).toInt().coerceIn(0,100) else 0
                active.addView(label(title + " • " + pct + "%",11,true),params(0,3))
                active.addView(ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply {
                    max=100; progress=pct
                },LinearLayout.LayoutParams(-1,dp(5)))
            }
        }
        if(n==0) active.addView(label("Koi active download nahi hai.",11).apply {
            setTextColor(Color.rgb(145,164,188))
        })
    }
    private fun label(t:String,s:Int,b:Boolean=false)=TextView(activity).apply {
        text=t;textSize=s.toFloat();setTextColor(Color.WHITE);if(b)typeface=Typeface.DEFAULT_BOLD
    }
    private fun button(t:String)=Button(activity).apply {
        text=t;isAllCaps=false;textSize=12f;setTextColor(Color.WHITE)
        background=GradientDrawable().apply { setColor(Color.rgb(23,107,80));cornerRadius=dp(12).toFloat() }
    }
    private fun rounded()=GradientDrawable().apply {
        setColor(Color.rgb(23,41,67));cornerRadius=dp(12).toFloat()
    }
    private fun params(t:Int=0,b:Int=0)=LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin=dp(t);bottomMargin=dp(b)
    }
    private fun dp(v:Int)=(v*activity.resources.displayMetrics.density).toInt()
    override fun onShow(){handler.removeCallbacks(task);handler.post(task)}
    override fun onHide(){handler.removeCallbacks(task)}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null)}
}
