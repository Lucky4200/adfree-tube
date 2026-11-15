package com.adfree.tube

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
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
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class AllInOneDownloaderSection(private val activity: Activity) : Section {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val input = EditText(activity)
    private val info = TextView(activity)
    private val active = LinearLayout(activity)
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
        input.hint="Paste video / audio URL"
        input.setSingleLine(true); input.textSize=14f; input.setTextColor(Color.WHITE)
        input.setHintTextColor(Color.GRAY); input.setPadding(dp(12),dp(11),dp(12),dp(11))
        input.background=rounded()
        root.addView(input,LinearLayout.LayoutParams(-1,dp(48)))
        val detect=button("Detect Link")
        detect.setOnClickListener { detect() }
        root.addView(detect,params(8,6))
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
    private fun detect() {
        val raw=input.text.toString().trim()
        val u=runCatching { Uri.parse(raw) }.getOrNull()
        if(u==null || u.scheme !in listOf("http","https") || u.host.isNullOrBlank()) {
            info.text="Valid http/https link paste karein."; return
        }
        mediaUrl=""; mime=""; info.text="Server se link check ho raha hai..."
        thread {
            var con:HttpURLConnection?=null
            try {
                con=URL(raw).openConnection() as HttpURLConnection
                con.requestMethod="HEAD"; con.connectTimeout=10000; con.readTimeout=10000
                con.instanceFollowRedirects=true
                con.setRequestProperty("User-Agent","Mozilla/5.0")
                con.connect()
                val type=con.contentType?.substringBefore(';')?.lowercase().orEmpty()
                val finalUrl=con.url.toString()
                val ext=Uri.parse(finalUrl).path.orEmpty().substringAfterLast('.',"").lowercase()
                val direct=type.startsWith("video/") || type.startsWith("audio/") ||
                    ext in setOf("mp4","m4v","webm","mov","mp3","m4a","aac","wav","ogg")
                if(direct) { mediaUrl=finalUrl; mime=type }
                activity.runOnUiThread {
                    info.text=if(direct) "Direct media detected ✓\\nType: " + type + "\\nReady to download."
                    else "Link open ho raha hai, lekin server ne direct video/audio file nahi batayi. Is page se media extract nahi hua."
                }
            } catch(e:Exception) {
                activity.runOnUiThread { info.text="Link check fail: " + (e.message ?: "Network error") }
            } finally { con?.disconnect() }
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
