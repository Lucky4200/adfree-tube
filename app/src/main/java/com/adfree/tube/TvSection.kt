package com.adfree.tube

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.HttpURLConnection
import java.net.URL

class Channel(val name: String, val country: String, val urls: MutableList<String>)

class TvSection(private val activity: Activity, private val isTv: Boolean) : Section {

    private val prefs = activity.getSharedPreferences("adfree", Activity.MODE_PRIVATE)
    private val player: ExoPlayer = ExoPlayer.Builder(activity).build()
    private val playerView = PlayerView(activity)
    private val status = TextView(activity)
    private val listView = ListView(activity)
    private val search = EditText(activity)
    private lateinit var rootLayout: LinearLayout
    private lateinit var listPanel: LinearLayout
    private lateinit var playerContainer: FrameLayout
    private lateinit var fullscreenButton: Button
    private var isFullscreen = false

    private var all: List<Channel> = emptyList()
    private var shown: List<Channel> = emptyList()
    private var country = "ALL"
    private var current: Channel? = null
    private var urlIdx = 0

    private val adapter = object : ArrayAdapter<String>(
        activity, android.R.layout.simple_list_item_1, ArrayList<String>()
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = super.getView(position, convertView, parent) as TextView
            v.setTextColor(Color.WHITE)
            v.textSize = if (isTv) 22f else 17f
            return v
        }
    }

    override val view: View

    init {
        playerView.player = player
        playerView.keepScreenOn = true
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                urlIdx++
                startUrl()
            }
        })

        status.setTextColor(Color.LTGRAY)
        status.setPadding(16, 8, 16, 8)

        search.hint = "Channel dhoondein..."
        search.setHintTextColor(Color.GRAY)
        search.setTextColor(Color.WHITE)
        search.setSingleLine()
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilter()
            }
        })

        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, pos, _ ->
            if (pos in shown.indices) play(shown[pos])
        }

        val filters = LinearLayout(activity)
        filters.orientation = LinearLayout.HORIZONTAL
        addFilterButton(filters, "All") { country = "ALL"; applyFilter() }
        addFilterButton(filters, "Pakistan") { country = "PK"; applyFilter() }
        addFilterButton(filters, "India") { country = "IN"; applyFilter() }
        addFilterButton(filters, "Extra") { country = "USR"; applyFilter() }
        addFilterButton(filters, "+Link") { addPlaylistDialog() }
        addFilterButton(filters, "Reload") { reload() }

        listPanel = LinearLayout(activity)
        listPanel.orientation = LinearLayout.VERTICAL
        listPanel.addView(filters)
        listPanel.addView(search)
        listPanel.addView(status)
        listPanel.addView(
            listView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        playerContainer = FrameLayout(activity)
        playerContainer.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenButton = Button(activity).apply {
            text = "⛶ Full Screen"
            isAllCaps = false
            textSize = 12f
            isFocusable = true
            setOnClickListener { toggleFullscreen() }
        }
        val buttonParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            val margin = (8 * activity.resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, margin)
        }
        playerContainer.addView(fullscreenButton, buttonParams)

        rootLayout = LinearLayout(activity)
        rootLayout.orientation = if (isTv) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        applyNormalLayout()
        view = rootLayout
        reload()
    }


    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        activity.requestedOrientation = if (isFullscreen) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        if (isFullscreen) {
            rootLayout.removeAllViews()
            rootLayout.addView(
                playerContainer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            fullscreenButton.text = "⤡ Exit Full Screen"
        } else {
            applyNormalLayout()
        }
    }

    private fun applyNormalLayout() {
        rootLayout.removeAllViews()
        fullscreenButton.text = "⛶ Full Screen"
        if (isTv) {
            rootLayout.addView(
                listPanel,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            )
            rootLayout.addView(
                playerContainer,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
            )
        } else {
            val h = (220 * activity.resources.displayMetrics.density).toInt()
            rootLayout.addView(
                playerContainer,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
            )
            rootLayout.addView(
                listPanel,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
        }
    }

    private fun addFilterButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(activity)
        b.text = label
        b.isAllCaps = false
        b.textSize = 12f
        b.minWidth = 0
        b.minimumWidth = 0
        b.setOnClickListener { action() }
        parent.addView(
            b,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
    }

    private fun addPlaylistDialog() {
        val input = EditText(activity)
        input.hint = "https://.../playlist.m3u"
        AlertDialog.Builder(activity)
            .setTitle("Playlist link (.m3u)")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val u = input.text.toString().trim()
                if (u.startsWith("https://") || u.startsWith("http://")) {
                    val set = HashSet<String>(prefs.getStringSet("playlists", emptySet()) ?: emptySet())
                    set.add(u)
                    prefs.edit().putStringSet("playlists", set).apply()
                    reload()
                } else {
                    Toast.makeText(activity, "Link http ya https se shuru hona chahiye", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reload() {
        status.text = "Channels load ho rahe hain..."
        Thread {
            val map = LinkedHashMap<String, Channel>()
            val sources = ArrayList<Pair<String, String>>(Config.PLAYLISTS)
            (prefs.getStringSet("playlists", emptySet()) ?: emptySet()).forEach {
                sources.add(Pair("USR", it))
            }
            for ((c, url) in sources) {
                val text = fetch(url) ?: continue
                for ((rawName, link) in parseM3u(text)) {
                    val name = cleanName(rawName)
                    if (name.isEmpty()) continue
                    val key = c + "|" + name.lowercase()
                    val ch = map.getOrPut(key) { Channel(name, c, ArrayList()) }
                    if (!ch.urls.contains(link)) ch.urls.add(link)
                }
            }
            map.values.forEach { ch ->
                ch.urls.sortBy { if (it.startsWith("https")) 0 else 1 }
            }
            val result = map.values.sortedBy { it.name.lowercase() }
            activity.runOnUiThread {
                all = result
                status.text = if (all.isEmpty()) {
                    "Koi channel nahi mila. Internet check karein ya +Link se playlist add karein."
                } else {
                    "${all.size} channels"
                }
                applyFilter()
            }
        }.start()
    }

    private fun applyFilter() {
        val q = search.text.toString().trim()
        shown = all.filter {
            (country == "ALL" || it.country == country) &&
                (q.isEmpty() || it.name.contains(q, ignoreCase = true))
        }
        adapter.clear()
        adapter.addAll(shown.map { "${it.name}  (${it.country})" })
        adapter.notifyDataSetChanged()
    }

    private fun fetch(url: String): String? {
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15000
            c.readTimeout = 25000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (c.responseCode == 200) {
                c.inputStream.bufferedReader().use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseM3u(text: String): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>()
        var name: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                name = line.substringAfterLast(",").trim()
            } else if (!line.startsWith("#")) {
                val n = name
                if (n != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                    out.add(Pair(n, line))
                }
                name = null
            }
        }
        return out
    }

    private fun cleanName(raw: String): String {
        return raw.replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim()
    }

    private fun play(ch: Channel) {
        current = ch
        urlIdx = 0
        startUrl()
    }

    private fun needsHls(url: String): Boolean {
        val u = url.lowercase()
        if (u.contains(".m3u8")) return true
        val other = listOf(".mp4", ".mkv", ".mpd", ".avi", ".mp3", ".aac", ".ts")
        return other.none { u.contains(it) }
    }

    private fun startUrl() {
        val ch = current ?: return
        if (urlIdx >= ch.urls.size) {
            Toast.makeText(activity, "Yeh channel abhi band hai: ${ch.name}", Toast.LENGTH_LONG).show()
            return
        }
        val url = ch.urls[urlIdx]
        val b = MediaItem.Builder().setUri(url)
        if (needsHls(url)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        player.setMediaItem(b.build())
        player.prepare()
        player.playWhenReady = true
    }

    override fun onBack(): Boolean {
        if (isFullscreen) {
            toggleFullscreen()
            return true
        }
        return false
    }

    override fun onShow() {
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
    }

    override fun onHide() {
        player.pause()
        activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onDestroy() {
        player.release()
    }
}
