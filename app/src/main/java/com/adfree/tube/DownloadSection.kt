package com.adfree.tube

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.os.Environment
import java.io.File

class DownloadSection(private val activity: Activity) : Section {
    private val manager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val list = LinearLayout(activity)
    private val handler = Handler(Looper.getMainLooper())
    override val view: View
    private val task = object : Runnable {
        override fun run() { refresh(); handler.postDelayed(this, 1800) }
    }
    init {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.rgb(6, 13, 25))
        }
        root.addView(label("DOWNLOAD LIST", 20, true))
        root.addView(label("Active, paused, failed aur completed downloads yahan nazar aayenge.", 12).apply {
            setTextColor(Color.rgb(145, 164, 188))
        }, params(5, 14))
        list.orientation = LinearLayout.VERTICAL
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        view = root
    }
    private fun refresh() {
        list.removeAllViews()
        var count = 0
        DownloadStore.ids(activity).forEach { id ->
            val cur = runCatching { manager.query(DownloadManager.Query().setFilterById(id)) }.getOrNull() ?: return@forEach
            cur.use {
                if (!it.moveToFirst()) return@use
                val state = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                count++
                val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Downloaded file"
                val downloaded = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val percent = if (total > 0L) (downloaded * 100L / total).toInt().coerceIn(0,100) else 0
                val stateText = when (state) {
                    DownloadManager.STATUS_SUCCESSFUL -> "Completed • 100%"
                    DownloadManager.STATUS_FAILED -> "Failed • Retry from source"
                    DownloadManager.STATUS_PAUSED -> "Paused • $percent%"
                    DownloadManager.STATUS_PENDING -> "Waiting • $percent%"
                    DownloadManager.STATUS_RUNNING -> "Downloading • $percent%"
                    else -> "Status unknown"
                }
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded()
                }
                row.addView(label((if (state == DownloadManager.STATUS_SUCCESSFUL) "✓  " else "⬇  ") + title, 13, true))
                row.addView(label(stateText, 11).apply { setTextColor(if (state == DownloadManager.STATUS_SUCCESSFUL) Color.rgb(124,245,197) else Color.rgb(180,205,230)) }, params(4,0))
                if (state == DownloadManager.STATUS_RUNNING || state == DownloadManager.STATUS_PAUSED || state == DownloadManager.STATUS_PENDING) {
                    row.addView(ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal).apply { max=100; progress=percent }, params(7,0))
                }
                list.addView(row, params(0,8))
            }
        }
        val exportPrefs = activity.getSharedPreferences("adfree_tube_media3_exports", Context.MODE_PRIVATE)
        val exportNames = exportPrefs.getStringSet("files", emptySet()).orEmpty()
        val exportDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: activity.filesDir
        exportNames.sortedDescending().forEach { name ->
            val file = File(exportDir, name)
            if (file.exists()) {
                count++
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded()
                }
                row.addView(label("✓  $name", 13, true))
                row.addView(label("Completed • ${file.length() / 1024L} KB • Media3 export", 11).apply {
                    setTextColor(Color.rgb(124,245,197))
                }, params(4,0))
                list.addView(row, params(0,8))
            }
        }
        val ytDir = YtDlpEngine.downloadDirectory(activity)
        val ytPrefs = activity.getSharedPreferences("adfree_tube_ytdlp_exports", Context.MODE_PRIVATE)
        ytPrefs.getStringSet("files", emptySet()).orEmpty().sortedDescending().forEach { name ->
            val file = File(ytDir, name)
            if (file.exists()) {
                count++
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded()
                }
                row.addView(label("✓  " + name, 13, true))
                row.addView(label("Completed • " + (file.length() / 1024L) + " KB • yt-dlp", 11).apply {
                    setTextColor(Color.rgb(124,245,197))
                }, params(4,0))
                list.addView(row, params(0,8))
            }
        }
        val publishedItems = activity.getSharedPreferences("adfree_tube_ytdlp_exports", Context.MODE_PRIVATE)
            .getStringSet("published_media", emptySet()).orEmpty().sortedDescending()
        publishedItems.forEach { entry ->
            val split = entry.split("|", limit = 2)
            if (split.size == 2) {
                count++
                val name = split[0]
                val mediaUri = Uri.parse(split[1])
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded()
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val ext = name.substringAfterLast(".", "").lowercase()
                        val type = if (ext in setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")) "audio/*" else "video/*"
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(mediaUri, type)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { activity.startActivity(intent) }
                            .onFailure { android.widget.Toast.makeText(activity, "Is media ko open karne ke liye compatible app nahi mili.", android.widget.Toast.LENGTH_LONG).show() }
                    }
                }
                row.addView(label("▶  " + name, 13, true))
                row.addView(label("Saved in Gallery / Music • Tap to open", 11).apply {
                    setTextColor(Color.rgb(124,245,197))
                }, params(4,0))
                list.addView(row, params(0,8))
            }
        }
        if (count == 0) list.addView(label("Abhi koi download nahi hai.", 12).apply {
            setTextColor(Color.rgb(145,164,188))
        })
    }
    private fun label(t:String,s:Int,b:Boolean=false)=TextView(activity).apply {
        text=t; textSize=s.toFloat(); setTextColor(Color.WHITE); if(b) typeface=Typeface.DEFAULT_BOLD
    }
    private fun rounded()=GradientDrawable().apply {
        setColor(Color.rgb(20,38,61)); cornerRadius=dp(11).toFloat()
        setStroke(dp(1),Color.rgb(48,70,94))
    }
    private fun params(t:Int=0,b:Int=0)=LinearLayout.LayoutParams(-1,ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin=dp(t); bottomMargin=dp(b)
    }
    private fun dp(v:Int)=(v*activity.resources.displayMetrics.density).toInt()
    override fun onShow(){handler.removeCallbacks(task);handler.post(task)}
    override fun onHide(){handler.removeCallbacks(task)}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null)}
}
