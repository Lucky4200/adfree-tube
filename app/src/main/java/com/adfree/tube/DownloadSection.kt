package com.adfree.tube

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Handler
import android.os.Looper

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
        root.addView(label("COMPLETED DOWNLOADS", 20, true))
        root.addView(label("Sirf mukammal 100% downloads yahan show honge.", 12).apply {
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
                if (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) != DownloadManager.STATUS_SUCCESSFUL) return@use
                count++
                val title = it.getString(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)) ?: "Downloaded file"
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = rounded()
                }
                row.addView(label("✓  " + title, 13, true))
                row.addView(label("Completed • 100%", 11).apply { setTextColor(Color.rgb(124,245,197)) }, params(4,0))
                list.addView(row, params(0,8))
            }
        }
        if (count == 0) list.addView(label("Abhi koi completed download nahi hai.", 12).apply {
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
