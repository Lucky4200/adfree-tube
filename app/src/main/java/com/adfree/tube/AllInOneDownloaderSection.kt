package com.adfree.tube

import android.app.Activity
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
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AllInOneDownloaderSection(private val activity: Activity) : Section {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val input = EditText(activity)
    private val info = TextView(activity)
    private val active = LinearLayout(activity)
    private val scanProgress = ProgressBar(activity).apply { isIndeterminate = true; visibility = View.GONE }
    private val scanStatus = TextView(activity).apply { text = "Ready to scan"; textSize = 11f; setTextColor(Color.rgb(145,164,188)) }
    private var mediaUrl = ""
    private var mime = ""
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
        root.addView(label("Video ya audio link paste karein aur Detect Link dabayein.",11).apply {
            setTextColor(Color.rgb(145,164,188))
        },params(5,10))
        val platforms = LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER }
        listOf("f  Facebook","◎  Instagram","♪  TikTok","▶  YouTube","X").forEach { item ->
            platforms.addView(TextView(activity).apply { text=item; textSize=8f; gravity=android.view.Gravity.CENTER; setTextColor(Color.WHITE); setPadding(dp(3),dp(7),dp(3),dp(7)); background=GradientDrawable().apply { setColor(Color.rgb(24,43,67)); cornerRadius=dp(9).toFloat(); setStroke(dp(1),Color.rgb(55,82,112)) } }, LinearLayout.LayoutParams(0,dp(32),1f).apply { marginStart=dp(2); marginEnd=dp(2) })
        }
        root.addView(platforms,params(0,8))
        input.hint="Copied link will appear here automatically"
        input.setSingleLine(true); input.textSize=12f; input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY); input.setPadding(dp(10),dp(10),dp(8),dp(10))
        input.background=rounded()
        val scanRow = LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
        scanRow.addView(input, LinearLayout.LayoutParams(0,dp(50),1f))
        scanRow.addView(Button(activity).apply {
            text="↻"; textSize=26f; isAllCaps=false; setTextColor(Color.WHITE)
            background=GradientDrawable().apply { shape=GradientDrawable.OVAL; setColor(Color.rgb(20,145,101)); setStroke(dp(2),Color.rgb(105,235,190)) }
            contentDescription="Paste copied link and scan"
            setOnClickListener { pasteClipboardAndDetect() }
        },LinearLayout.LayoutParams(dp(56),dp(56)).apply { marginStart=dp(10) })
        root.addView(scanRow,params(8,5))
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
            if (clipboard.hasPrimaryClip() && clipboard.primaryClip!!.itemCount > 0) clipboard.primaryClip!!.getItemAt(0).coerceToText(activity)?.toString()?.trim().orEmpty() else ""
        }.getOrDefault("")
        val link = copied.lineSequence().firstOrNull { it.startsWith("https://", true) || it.startsWith("http://", true) || it.startsWith("www.", true) }.orEmpty()
        if (link.isBlank()) {
            Toast.makeText(activity,"Pehle video link Copy karein, phir gol Scan button dabayein.",Toast.LENGTH_LONG).show()
            scanStatus.text="Clipboard mein link nahi mila"
            return
        }
        input.setText(link)
        detect()
    }

    private fun detect() {
        val raw = input.text.toString().trim()
        val normalized = if (raw.startsWith("www.", true) || raw.startsWith("youtu.be/", true) || raw.startsWith("youtube.com/", true)) "https://$raw" else raw
        val u = runCatching { Uri.parse(normalized) }.getOrNull()
        if (u == null || u.scheme !in listOf("http", "https") || u.host.isNullOrBlank()) {
            info.text = "Valid http/https link paste karein."
            return
        }
        mediaUrl = ""; mime = ""; info.text = "Link detect ho raha hai..."
        scanProgress.visibility = View.VISIBLE
        scanStatus.text = "Scanning link..."
        thread {
            try {
                val host = u.host.orEmpty().lowercase().removePrefix("www.")
                val isYouTube = host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
                if (isYouTube) {
                    val videoId = when {
                        host == "youtu.be" -> u.pathSegments.firstOrNull().orEmpty()
                        u.path == "/watch" -> u.getQueryParameter("v").orEmpty()
                        u.pathSegments.firstOrNull() in listOf("shorts", "embed", "live") -> u.pathSegments.getOrNull(1).orEmpty()
                        else -> ""
                    }
                    var title = ""
                    if (videoId.isNotBlank()) runCatching {
                        val endpoint = "https://www.youtube.com/oembed?url=" + URLEncoder.encode(normalized, "UTF-8") + "&format=json"
                        val c = URL(endpoint).openConnection() as HttpURLConnection
                        c.connectTimeout = 8000; c.readTimeout = 8000
                        c.setRequestProperty("User-Agent", "Mozilla/5.0")
                        c.inputStream.bufferedReader().use { title = JSONObject(it.readText()).optString("title") }
                        c.disconnect()
                    }
                    activity.runOnUiThread {
                        info.text = if (videoId.isBlank()) "YouTube link pehchan liya, lekin video ID nahi mili." else "YouTube video detect ho gayi ✓" + (if (title.isNotBlank()) "\nTitle: $title" else "") + "\n\nYeh YouTube page-link hai, direct MP4 file nahi. Is direct downloader se video file extract nahi ho sakti. YouTube ka official Download option use karein."
                    }
                    return@thread
                }
                var type = ""; var finalUrl = normalized; var code = -1
                var con: HttpURLConnection? = null
                try {
                    con = URL(normalized).openConnection() as HttpURLConnection
                    con.requestMethod = "HEAD"; con.connectTimeout = 10000; con.readTimeout = 10000
                    con.instanceFollowRedirects = true; con.setRequestProperty("User-Agent", "Mozilla/5.0")
                    code = con.responseCode; type = con.contentType?.substringBefore(";")?.lowercase().orEmpty(); finalUrl = con.url.toString()
                } finally { con?.disconnect() }
                if (code >= 400 || type.isBlank() || type == "application/octet-stream" || type == "text/html") {
                    var get: HttpURLConnection? = null
                    try {
                        get = URL(finalUrl).openConnection() as HttpURLConnection
                        get.requestMethod = "GET"; get.connectTimeout = 10000; get.readTimeout = 10000
                        get.instanceFollowRedirects = true; get.setRequestProperty("User-Agent", "Mozilla/5.0"); get.setRequestProperty("Range", "bytes=0-0")
                        code = get.responseCode; type = get.contentType?.substringBefore(";")?.lowercase().orEmpty(); finalUrl = get.url.toString()
                    } finally { get?.disconnect() }
                }
                val ext = Uri.parse(finalUrl).path.orEmpty().substringAfterLast(".", "").lowercase()
                val direct = type.startsWith("video/") || type.startsWith("audio/") || ext in setOf("mp4","m4v","webm","mov","mp3","m4a","aac","wav","ogg")
                if (direct) { mediaUrl = finalUrl; mime = type }
                activity.runOnUiThread {
                    scanProgress.visibility = View.GONE
                    scanStatus.text = "Scan complete"
                    info.text = if (direct) "Direct media detect ho gayi ✓\nType: $type\nDownload ke liye ready." else "Link mil gaya, lekin website ne direct video/audio file expose nahi ki. Page-link se media extract nahi hua."
                }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    scanProgress.visibility = View.GONE
                    scanStatus.text = "Scan failed"
                    info.text = "Link detect nahi ho saka: " + (e.message ?: "Network error")
                }
            }
        }
    }
    private fun startDownload() {
        if(mediaUrl.isBlank()) {
            Toast.makeText(activity,"Pehle direct media link detect karein.",Toast.LENGTH_LONG).show(); return
        }
        try {
            DownloadStore.enqueue(activity,mediaUrl,"Media download",mime)
            info.text="Download shuru ho gaya. Complete hone par Downloads tab mein nazar aayega."
            mediaUrl=""
            refresh()
        } catch(e:Exception) { info.text=e.message ?: "Download start nahi hua." }
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
