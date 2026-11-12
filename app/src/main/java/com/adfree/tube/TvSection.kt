package com.adfree.tube

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ScrollView
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
    private lateinit var channelsButton: Button
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
            val channel = shown.getOrNull(position)
            val playing = channel != null && channel == current
            v.setTextColor(if (playing) Color.parseColor("#7CF5C5") else Color.parseColor("#E8EEF8"))
            v.textSize = if (isTv) 19f else 15f
            v.typeface = if (playing) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-medium", Typeface.NORMAL)
            v.setPadding(dp(16), dp(13), dp(16), dp(13))
            v.background = rounded(if (playing) "#183E3A" else "#14233B", 12)
            v.minimumHeight = dp(if (isTv) 58 else 48)
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

        status.setTextColor(Color.parseColor("#9EADC4"))
        status.textSize = 12f
        status.setPadding(dp(14), dp(8), dp(14), dp(8))

        search.hint = "⌕  Search channels..."
        search.setHintTextColor(Color.parseColor("#8293AE"))
        search.setTextColor(Color.WHITE)
        search.textSize = 14f
        search.setSingleLine()
        search.setPadding(dp(14), dp(11), dp(14), dp(11))
        search.background = rounded("#172943", 14)
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
        listPanel.setPadding(dp(12), dp(12), dp(12), dp(10))
        listPanel.setBackgroundColor(Color.parseColor("#0B1729"))

        val heading = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(3), dp(4), dp(10))
        }
        val title = TextView(activity).apply {
            text = "LIVE TV"
            setTextColor(Color.WHITE)
            textSize = if (isTv) 23f else 19f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        val subtitle = TextView(activity).apply {
            text = "Explore channels • Watch live"
            setTextColor(Color.parseColor("#8FA2BE"))
            textSize = 11f
        }
        heading.addView(title)
        heading.addView(subtitle)
        listPanel.addView(heading)

        val filterScroll = ScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            addView(filters)
        }
        listPanel.addView(filterScroll)
        listPanel.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10); bottomMargin = dp(4) })
        listPanel.addView(status)
        listPanel.addView(
            listView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        playerContainer = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
        }
        playerContainer.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        fullscreenButton = Button(activity).apply {
            text = "⛶  Full Screen"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            background = rounded("#D91C2D42", 22)
            elevation = dp(5).toFloat()
            setPadding(dp(14), dp(7), dp(14), dp(7))
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

        channelsButton = Button(activity).apply {
            text = "☷  Channels"
            isAllCaps = false
            textSize = 12f
            setTextColor(Color.WHITE)
            background = rounded("#E61B2E49", 22)
            elevation = dp(5).toFloat()
            setPadding(dp(14), dp(7), dp(14), dp(7))
            isFocusable = true
            visibility = View.GONE
            setOnClickListener { showChannelPicker() }
        }
        val channelsParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.START
        ).apply {
            val margin = (8 * activity.resources.displayMetrics.density).toInt()
            setMargins(margin, margin, margin, margin)
        }
        playerContainer.addView(channelsButton, channelsParams)

        rootLayout = LinearLayout(activity)
        rootLayout.orientation = if (isTv) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        rootLayout.setBackgroundColor(Color.parseColor("#07111F"))
        applyNormalLayout()
        view = rootLayout
        reload()
    }


    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            activity.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            enterImmersiveMode()
            rootLayout.removeAllViews()
            rootLayout.addView(
                playerContainer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            fullscreenButton.text = "⤡ Exit Full Screen"
            channelsButton.visibility = View.VISIBLE
        } else {
            channelsButton.visibility = View.GONE
            exitImmersiveMode()
            activity.requestedOrientation = if (isTv) {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            applyNormalLayout()
        }
    }

    private fun enterImmersiveMode() {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun exitImmersiveMode() {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun showChannelPicker() {
        if (all.isEmpty()) {
            Toast.makeText(activity, "Channel list abhi load nahi hui", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = all.indexOf(current).coerceAtLeast(0)
        val labels = all.map { ch ->
            val prefix = if (ch == current) "✓ NOW PLAYING  •  " else ""
            "$prefix${ch.name} (${ch.country})"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("TV Channels")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                if (which in all.indices) play(all[which])
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
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

    private fun dp(value: Int): Int =
        (value * activity.resources.displayMetrics.density).toInt()

    private fun rounded(color: String, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(Color.parseColor(color))
            cornerRadius = dp(radius).toFloat()
        }

    private fun addFilterButton(parent: LinearLayout, label: String, action: () -> Unit) {
        val b = Button(activity)
        b.text = label
        b.isAllCaps = false
        b.textSize = 11f
        b.setTextColor(Color.parseColor("#DCE8F8"))
        b.background = rounded("#1B304B", 18)
        b.elevation = dp(2).toFloat()
        b.setPadding(dp(8), dp(6), dp(8), dp(6))
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
        adapter.addAll(shown.map { if (it == current) "✓ ${it.name}  (${it.country}) — PLAYING" else "${it.name}  (${it.country})" })
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
        applyFilter()
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
        activity.requestedOrientation = if (isFullscreen) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else if (isTv) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onHide() {
        player.pause()
        if (isFullscreen) {
            isFullscreen = false
            channelsButton.visibility = View.GONE
            exitImmersiveMode()
            applyNormalLayout()
        }
        activity.requestedOrientation = if (isTv) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    override fun onDestroy() {
        player.release()
    }
}
