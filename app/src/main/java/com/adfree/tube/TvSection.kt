package com.adfree.tube

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.view.LayoutInflater
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.os.Handler
import android.os.Looper
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
import androidx.media3.ui.AspectRatioFrameLayout
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class Channel(val name: String, val country: String, val urls: MutableList<String>, var logoUrl: String? = null)
private data class ParsedChannel(val name: String, val country: String, val url: String, val logoUrl: String?)

class TvSection(private val activity: Activity, private val isTv: Boolean, private val onFullscreenChanged: (Boolean) -> Unit = {}) : Section {

    private val prefs = activity.getSharedPreferences("adfree", Activity.MODE_PRIVATE)
    private val player: ExoPlayer = ExoPlayer.Builder(activity).build()
    private val playerView: PlayerView by lazy { LayoutInflater.from(activity).inflate(R.layout.tv_player_view, null) as PlayerView }
    private val status = TextView(activity)
    private val listView = ListView(activity)
    private val search = EditText(activity)
    private lateinit var rootLayout: LinearLayout
    private lateinit var listPanel: LinearLayout
    private lateinit var playerContainer: FrameLayout
    private lateinit var fullscreenButton: Button
    private lateinit var ratioButton: Button
    private lateinit var channelsButton: Button
    private lateinit var channelOverlay: FrameLayout
    private lateinit var overlayList: ListView
    private lateinit var countryFilters: LinearLayout
    private lateinit var searchRow: LinearLayout
    private lateinit var searchToggle: TextView
    private val countryButtons = LinkedHashMap<String, TextView>()
    private val loadedCountryCodes = ConcurrentHashMap.newKeySet<String>()
    private val logoCache = ConcurrentHashMap<String, Bitmap>()
    private val logoExecutor = Executors.newFixedThreadPool(3)
    private var isFullscreen = false
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideChannelButton = Runnable { channelsButton.visibility = View.GONE }

    private var all: List<Channel> = emptyList()
    private var shown: List<Channel> = emptyList()
    private var country = "ALL"
    private var current: Channel? = null
    private var urlIdx = 0

    private val adapter = object : ArrayAdapter<String>(
        activity, android.R.layout.simple_list_item_1, ArrayList<String>()
    ) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val channel = shown.getOrNull(position)
            val playing = channel != null && channel == current
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(7), dp(10), dp(7))
                background = rounded(if (playing) "#183E3A" else "#14233B", 12)
                minimumHeight = dp(if (isTv) 62 else 54)
            }
            val logoFrame = FrameLayout(activity)
            val fallback = TextView(activity).apply {
                text = channel?.let { countryFlag(it.country) } ?: "📺"
                textSize = if (isTv) 23f else 20f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                background = rounded("#263D59", 10)
            }
            logoFrame.addView(fallback, FrameLayout.LayoutParams(dp(42), dp(42)))
            val logoUrl = channel?.logoUrl
            if (!logoUrl.isNullOrBlank()) {
                val image = ImageView(activity).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    tag = logoUrl
                }
                logoFrame.addView(image, FrameLayout.LayoutParams(dp(42), dp(42)))
                val cached = logoCache[logoUrl]
                if (cached != null) {
                    image.setImageBitmap(cached)
                    fallback.visibility = View.GONE
                } else {
                    logoExecutor.execute {
                        try {
                            val connection = URL(logoUrl).openConnection() as HttpURLConnection
                            connection.connectTimeout = 7000
                            connection.readTimeout = 7000
                            connection.instanceFollowRedirects = true
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                            val bitmap = if (connection.responseCode == 200) {
                                connection.inputStream.use { BitmapFactory.decodeStream(it) }
                            } else null
                            connection.disconnect()
                            if (bitmap != null) {
                                logoCache[logoUrl] = bitmap
                                activity.runOnUiThread {
                                    if (image.tag == logoUrl) {
                                        image.setImageBitmap(bitmap)
                                        fallback.visibility = View.GONE
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                    }
                }
            }
            row.addView(logoFrame, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                marginEnd = dp(12)
            })
            val label = TextView(activity).apply {
                text = if (playing) "✓  " + (channel?.name ?: getItem(position)) + "  •  PLAYING"
                    else channel?.name ?: getItem(position)
                setTextColor(if (playing) Color.parseColor("#7CF5C5") else Color.parseColor("#E8EEF8"))
                textSize = if (isTv) 18f else 14f
                typeface = if (playing) Typeface.DEFAULT_BOLD else Typeface.create("sans-serif-medium", Typeface.NORMAL)
                maxLines = 2
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return row
        }
    }

    override val view: View

    init {
        playerView.player = player
        playerView.keepScreenOn = true
        playerView.setShutterBackgroundColor(Color.BLACK)
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        playerView.isFocusable = true
        playerView.setOnClickListener {
            if (isFullscreen) revealChannelControl()
        }
        playerView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && isFullscreen) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { changeChannel(1); true }
                    KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { changeChannel(-1); true }
                    else -> false
                }
            } else false
        }
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                urlIdx++
                startUrl()
            }
        })

        status.setTextColor(Color.parseColor("#9EADC4"))
        status.textSize = 12f
        status.setPadding(dp(5), dp(2), dp(4), dp(2))

        search.hint = "⌕  Search channels..."
        search.setHintTextColor(Color.parseColor("#8293AE"))
        search.setTextColor(Color.WHITE)
        search.textSize = 13f
        search.setSingleLine()
        search.setPadding(dp(12), dp(7), dp(12), dp(7))
        search.background = rounded("#172943", 12)
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

        countryFilters = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buildCountryButtons()
        addFilterButton(countryFilters, "🌍 More countries") { showMoreCountries(Locale.getISOCountries().toList()) }
        addFilterButton(countryFilters, "+ Link") { addPlaylistDialog() }
        addFilterButton(countryFilters, "↻") { reload() }

        listPanel = LinearLayout(activity)
        listPanel.orientation = LinearLayout.VERTICAL
        listPanel.setPadding(dp(10), dp(7), dp(10), dp(5))
        listPanel.setBackgroundColor(Color.parseColor("#0B1729"))

        val heading = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(1), dp(4), dp(5))
        }
        val title = TextView(activity).apply {
            text = "LIVE TV"
            setTextColor(Color.WHITE)
            textSize = if (isTv) 20f else 17f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        }
        val subtitle = TextView(activity).apply {
            text = "Choose a country to browse live channels"
            setTextColor(Color.parseColor("#8FA2BE"))
            textSize = 10f
        }
        heading.addView(title)
        heading.addView(subtitle)
        listPanel.addView(heading)

        val filterScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isSmoothScrollingEnabled = true
            addView(countryFilters, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        listPanel.addView(filterScroll)

        searchRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(search, LinearLayout.LayoutParams(0, dp(42), 1f))
        }
        listPanel.addView(searchRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(7); bottomMargin = dp(2) })

        val toolsRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        toolsRow.addView(status, LinearLayout.LayoutParams(0, dp(34), 1f))
        searchToggle = TextView(activity).apply {
            text = "⌕"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded("#203650", 12)
            isFocusable = true
            contentDescription = "Search channels"
            setOnClickListener {
                searchRow.visibility = if (searchRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                if (searchRow.visibility == View.VISIBLE) {
                    search.requestFocus()
                    val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                } else {
                    search.setText("")
                    val imm = activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(search.windowToken, 0)
                }
            }
        }
        toolsRow.addView(searchToggle, LinearLayout.LayoutParams(dp(42), dp(38)))
        listPanel.addView(toolsRow)
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

        ratioButton = Button(activity).apply {
            text = "▣  Fit"
            isAllCaps = false
            textSize = 11f
            setTextColor(Color.WHITE)
            background = rounded("#D91C2D42", 22)
            elevation = dp(5).toFloat()
            setPadding(dp(11), dp(7), dp(11), dp(7))
            isFocusable = true
            contentDescription = "Video aspect ratio"
            setOnClickListener { showRatioOptions() }
        }
        val ratioParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply {
            val margin = dp(8)
            setMargins(margin, margin, margin, margin)
        }
        playerContainer.addView(ratioButton, ratioParams)

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
            setOnClickListener { showChannelOverlay() }
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

        channelOverlay = FrameLayout(activity).apply {
            setBackgroundColor(Color.parseColor("#66000000"))
            visibility = View.GONE
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { hideChannelOverlay() }
        }
        val overlayPanel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded("#F0122034", 18)
            elevation = dp(14).toFloat()
            isFocusable = true
        }
        val overlayHeader = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val overlayTitle = TextView(activity).apply {
            text = "LIVE CHANNELS"
            textSize = if (isTv) 18f else 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }
        overlayHeader.addView(overlayTitle, LinearLayout.LayoutParams(0, dp(42), 1f))
        val closeOverlay = TextView(activity).apply {
            text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded("#34465D", 10)
            isFocusable = true
            contentDescription = "Close channel list"
            setOnClickListener { hideChannelOverlay() }
        }
        overlayHeader.addView(closeOverlay, LinearLayout.LayoutParams(dp(42), dp(42)))
        overlayPanel.addView(overlayHeader)

        val overlayHint = TextView(activity).apply {
            text = "Select a channel • Video keeps playing"
            textSize = 10f
            setTextColor(Color.parseColor("#AFC0D5"))
            setPadding(dp(2), 0, 0, dp(8))
        }
        overlayPanel.addView(overlayHint)
        overlayList = ListView(activity).apply {
            adapter = this@TvSection.adapter
            divider = android.graphics.drawable.ColorDrawable(Color.parseColor("#30445D"))
            dividerHeight = dp(1)
            cacheColorHint = Color.TRANSPARENT
            selector = rounded("#426B88", 8)
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            isFocusable = true
            isFocusableInTouchMode = true
            setOnItemClickListener { _, _, pos, _ ->
                if (pos in shown.indices) {
                    play(shown[pos])
                    hideChannelOverlay()
                    playerView.requestFocus()
                }
            }
        }
        overlayPanel.addView(overlayList, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val panelParams = FrameLayout.LayoutParams(
            if (isTv) dp(390) else (activity.resources.displayMetrics.widthPixels * 0.82f).toInt(),
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.END or Gravity.CENTER_VERTICAL
        ).apply {
            setMargins(dp(10), dp(10), dp(10), dp(10))
        }
        channelOverlay.addView(overlayPanel, panelParams)
        playerContainer.addView(channelOverlay, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))

        playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (isFullscreen) {
                    if (visibility == View.VISIBLE) revealChannelControl()
                    else {
                        uiHandler.removeCallbacks(hideChannelButton)
                        channelsButton.visibility = View.GONE
                    }
                }
            }
        )

        rootLayout = LinearLayout(activity)
        rootLayout.orientation = if (isTv) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        rootLayout.setBackgroundColor(Color.parseColor("#07111F"))
        applyNormalLayout()
        view = rootLayout
        reload()
    }


    private fun showRatioOptions() {
        val labels = arrayOf(
            "Fit to Screen (recommended)",
            "Original / Fit",
            "Fill Screen",
            "Zoom to Fill"
        )
        AlertDialog.Builder(activity)
            .setTitle("Video Screen Ratio")
            .setItems(labels) { dialog, which ->
                val mode = when (which) {
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    3 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                playerView.resizeMode = mode
                ratioButton.text = when (which) {
                    2 -> "▣  Fill"
                    3 -> "▣  Zoom"
                    1 -> "▣  Original"
                    else -> "▣  Fit"
                }
                dialog.dismiss()
            }
            .show()
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
            fullscreenButton.visibility = View.GONE
            onFullscreenChanged(true)
            revealChannelControl()
        } else {
            uiHandler.removeCallbacks(hideChannelButton)
            channelsButton.visibility = View.GONE
            fullscreenButton.visibility = View.VISIBLE
            onFullscreenChanged(false)
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

    private fun revealChannelControl() {
        uiHandler.removeCallbacks(hideChannelButton)
        channelsButton.visibility = View.VISIBLE
        uiHandler.postDelayed(hideChannelButton, 3500)
    }

    private fun changeChannel(direction: Int) {
        if (all.isEmpty()) return
        val currentIndex = all.indexOf(current)
        val next = if (currentIndex < 0) 0 else (currentIndex + direction + all.size) % all.size
        play(all[next])
        if (isFullscreen) revealChannelControl()
    }

    private fun showChannelOverlay() {
        uiHandler.removeCallbacks(hideChannelButton)
        channelsButton.visibility = View.GONE
        if (all.isEmpty()) {
            Toast.makeText(activity, "Channel list abhi load nahi hui", Toast.LENGTH_SHORT).show()
            if (isFullscreen) revealChannelControl()
            return
        }
        applyFilter()
        channelOverlay.visibility = View.VISIBLE
        channelOverlay.bringToFront()
        overlayList.setSelection(shown.indexOf(current).coerceAtLeast(0))
        overlayList.requestFocus()
    }

    private fun hideChannelOverlay() {
        if (!::channelOverlay.isInitialized) return
        channelOverlay.visibility = View.GONE
        if (isFullscreen) revealChannelControl()
    }

    private fun applyNormalLayout() {
        if (::channelOverlay.isInitialized) channelOverlay.visibility = View.GONE
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
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = dp(6)
            }
        )
    }

    private fun buildCountryButtons() {
        countryFilters.removeAllViews()
        countryButtons.clear()
        val primary = listOf(
            Triple("PK", "🇵🇰", "Pakistan"),
            Triple("IN", "🇮🇳", "India"),
            Triple("BD", "🇧🇩", "Bangladesh"),
            Triple("SA", "🇸🇦", "Saudi Arabia"),
            Triple("AF", "🇦🇫", "Afghanistan")
        )
        addCountryChip("ALL", "🌐", "All")
        primary.forEach { (code, flag, label) -> addCountryChip(code, flag, label) }
        val extras = all.map { it.country }.distinct()
            .filter { it !in listOf("PK", "IN", "BD", "SA", "AF", "USR", "OTHER") }
            .sorted()
        extras.forEach { code ->
            addCountryChip(code, countryFlag(code), countryLabel(code))
        }
        if (all.any { it.country == "OTHER" }) addCountryChip("OTHER", "🌍", "Other")
        if (all.any { it.country == "USR" }) addCountryChip("USR", "📂", "My")
        refreshCountryChips()
    }

    private fun addCountryChip(code: String, flag: String, label: String) {
        val chip = TextView(activity).apply {
            text = "$flag  $label"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#DCE8F8"))
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            background = rounded("#1B304B", 18)
            setPadding(dp(11), dp(8), dp(11), dp(8))
            isFocusable = true
            setOnClickListener {
                country = code
                applyFilter()
                refreshCountryChips()
            }
        }
        countryButtons[code] = chip
        countryFilters.addView(chip, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)
        ).apply { marginEnd = dp(6) })
    }

    private fun refreshCountryChips() {
        countryButtons.forEach { (code, chip) ->
            val selected = country == code
            chip.background = rounded(if (selected) "#176B50" else "#1B304B", 18)
            chip.setTextColor(if (selected) Color.WHITE else Color.parseColor("#DCE8F8"))
        }
    }

    private fun showMoreCountries(codes: List<String>) {
        val available = codes.distinct().filter { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
            .sortedBy { countryLabel(it).lowercase(Locale.ROOT) }
        val labels = available.map { code ->
            "${countryFlag(code)}  ${countryLabel(code)}"
        }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("More countries")
            .setItems(labels) { dialog, which ->
                if (which in available.indices) {
                    val selectedCode = available[which]
                    country = selectedCode
                    applyFilter()
                    refreshCountryChips()
                    if (!loadedCountryCodes.contains(selectedCode)) loadCountryPlaylist(selectedCode)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun loadCountryPlaylist(code: String) {
        if (!loadedCountryCodes.add(code)) return
        status.text = "Loading ${countryLabel(code)} channels..."
        Thread {
            val url = "https://iptv-org.github.io/iptv/countries/${code.lowercase(Locale.ROOT)}.m3u"
            val text = fetch(url)
            val parsed = if (text.isNullOrBlank()) emptyList() else parseM3u(text, code)
            activity.runOnUiThread {
                if (parsed.isEmpty()) {
                    status.text = "Is country ki playlist mein channels available nahi hain"
                    return@runOnUiThread
                }
                val merged = LinkedHashMap<String, Channel>()
                all.forEach { merged[it.country + "|" + it.name.lowercase(Locale.ROOT)] = it }
                parsed.forEach { entry ->
                    val name = cleanName(entry.name)
                    if (name.isBlank()) return@forEach
                    val key = entry.country + "|" + name.lowercase(Locale.ROOT)
                    val channel = merged.getOrPut(key) {
                        Channel(name, entry.country, ArrayList(), entry.logoUrl)
                    }
                    if (!channel.urls.contains(entry.url)) channel.urls.add(entry.url)
                    if (channel.logoUrl.isNullOrBlank()) channel.logoUrl = entry.logoUrl
                }
                all = merged.values.sortedWith(compareBy<Channel> { it.country }.thenBy { it.name.lowercase(Locale.ROOT) })
                buildCountryButtons()
                applyFilter()
            }
        }.start()
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
            for ((fallbackCountry, url) in sources) {
                if (fallbackCountry.length == 2) loadedCountryCodes.add(fallbackCountry)
                if (fallbackCountry == "WORLD") loadedCountryCodes.addAll(Locale.getISOCountries().toList())
                val text = fetch(url) ?: continue
                for (entry in parseM3u(text, fallbackCountry)) {
                    val name = cleanName(entry.name)
                    if (name.isEmpty()) continue
                    val key = entry.country + "|" + name.lowercase()
                    val ch = map.getOrPut(key) { Channel(name, entry.country, ArrayList(), entry.logoUrl) }
                    if (!ch.urls.contains(entry.url)) ch.urls.add(entry.url)
                    if (ch.logoUrl.isNullOrBlank() && !entry.logoUrl.isNullOrBlank()) ch.logoUrl = entry.logoUrl
                }
            }
            map.values.forEach { ch ->
                ch.urls.sortBy { if (it.startsWith("https")) 0 else 1 }
            }
            val priority = listOf("PK", "IN", "BD", "SA", "AF")
            val result = map.values.sortedWith(
                compareBy<Channel> {
                    val index = priority.indexOf(it.country)
                    if (index >= 0) index else priority.size
                }.thenBy { it.country }.thenBy { it.name.lowercase() }
            )
            activity.runOnUiThread {
                all = result
                buildCountryButtons()
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
        val priorityCodes = setOf("PK", "IN", "BD", "SA", "AF")
        shown = all.filter {
            val countryMatch = when (country) {
                "ALL" -> true
                "OTHER" -> it.country !in priorityCodes && it.country != "USR"
                else -> it.country == country
            }
            countryMatch && (q.isEmpty() || it.name.contains(q, ignoreCase = true))
        }
        adapter.clear()
        adapter.addAll(shown.map {
            if (it == current) "✓ ${it.name}  — PLAYING" else it.name
        })
        adapter.notifyDataSetChanged()
        if (::countryFilters.isInitialized) refreshCountryChips()
        status.text = if (shown.isEmpty()) "No channels found in this country" else "${shown.size} channels"
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

    private fun parseM3u(text: String, fallbackCountry: String): List<ParsedChannel> {
        val out = ArrayList<ParsedChannel>()
        var name: String? = null
        var detectedCountry = fallbackCountry
        var logoUrl: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                name = line.substringAfterLast(",").trim()
                val attributes = Regex("""([\w-]+)\s*=\s*["']([^"']*)["']""")
                    .findAll(line)
                    .associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[2].trim() }
                val countryValue = attributes["tvg-country"]?.split(",")?.firstOrNull()?.trim().orEmpty()
                detectedCountry = if (fallbackCountry == "WORLD") normalizeCountry(countryValue) else fallbackCountry
                logoUrl = attributes["tvg-logo"]?.takeIf {
                    it.startsWith("https://", true) || it.startsWith("http://", true)
                }
            } else if (!line.startsWith("#")) {
                val n = name
                if (n != null && (line.startsWith("http://") || line.startsWith("https://"))) {
                    out.add(ParsedChannel(n, detectedCountry, line, logoUrl))
                }
                name = null
                logoUrl = null
                detectedCountry = fallbackCountry
            }
        }
        return out
    }

    private fun normalizeCountry(value: String): String {
        val name = value.trim()
        if (name.isEmpty()) return "OTHER"
        val upper = name.uppercase(Locale.ROOT)
        if (upper.length == 2 && upper.all { it in 'A'..'Z' }) return upper
        val aliases = mapOf("UK" to "GB", "USA" to "US", "UAE" to "AE", "KOREA" to "KR")
        aliases[upper]?.let { return it }
        return Locale.getISOCountries().firstOrNull {
            Locale("", it).displayCountry.equals(name, ignoreCase = true)
        } ?: "OTHER"
    }

    private fun countryFlag(code: String): String {
        val c = code.uppercase()
        if (c.length != 2 || c.any { it !in 'A'..'Z' }) return "🌍"
        val points = c.map { 0x1F1E6 + (it - 'A') }
        return String(Character.toChars(points[0])) + String(Character.toChars(points[1]))
    }

    private fun countryLabel(code: String): String = when (code.uppercase()) {
        "PK" -> "Pakistan"
        "IN" -> "India"
        "BD" -> "Bangladesh"
        "SA" -> "Saudi Arabia"
        "AF" -> "Afghanistan"
        "USR" -> "My playlist"
        "OTHER" -> "Other country"
        else -> Locale("", code.uppercase()).displayCountry.ifBlank { code.uppercase() }
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
            uiHandler.removeCallbacks(hideChannelButton)
            channelsButton.visibility = View.GONE
            fullscreenButton.visibility = View.VISIBLE
            onFullscreenChanged(false)
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
        uiHandler.removeCallbacksAndMessages(null)
        if (::channelOverlay.isInitialized) channelOverlay.visibility = View.GONE
        logoExecutor.shutdownNow()
        logoCache.values.forEach { it.recycle() }
        logoCache.clear()
        player.release()
    }
}
