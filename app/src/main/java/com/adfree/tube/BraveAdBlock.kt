package com.adfree.tube

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.json.JSONObject
import org.mlm.adblock.AdblockEngine
import org.mlm.adblock.RequestTypeMapper
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Brave adblock-rust integration for the app's embedded WebViews.
 *
 * Filter rules are downloaded in the background, cached locally, and refreshed daily.
 * Requests are checked by the native engine; page cosmetics and scriptlets are injected
 * after navigation completes.
 */
object BraveAdBlock {
    private const val PREFS = "brave_adblock"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L
    private const val MAX_FILTER_CHARS = 12 * 1024 * 1024

    private val executor = Executors.newSingleThreadExecutor()
    private val updateRunning = AtomicBoolean(false)
    private val pageUrls = Collections.synchronizedMap(WeakHashMap<WebView, String>())

    @Volatile
    private var engine: AdblockEngine? = null

    private val filterLists = listOf(
        "https://easylist.to/easylist/easylist.txt",
        "https://easylist.to/easylist/easyprivacy.txt",
        "https://filters.adtidy.org/extension/chromium/filters/2.txt",
        "https://filters.adtidy.org/extension/chromium/filters/3.txt",
        "https://filters.adtidy.org/extension/chromium/filters/14.txt"
    )

    fun initialize(context: Context) {
        if (engine != null) return

        synchronized(this) {
            if (engine != null) return
            val app = context.applicationContext
            val nativeEngine = AdblockEngine.create()
            engine = nativeEngine

            try {
                val cache = java.io.File(app.filesDir, "brave-adblock.bin")
                if (cache.exists()) nativeEngine.deserializeFrom(cache)
                val resources = app.assets.open("adblock/resources.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                nativeEngine.loadResources(resources)
            } catch (_: Exception) {
                // Keep the engine alive; network filters can still be loaded below.
            }

            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastUpdate = prefs.getLong(KEY_UPDATED_AT, 0L)
            if (lastUpdate == 0L || System.currentTimeMillis() - lastUpdate >= UPDATE_INTERVAL_MS) {
                updateFilters(app)
            }
        }
    }

    private fun updateFilters(context: Context) {
        if (!updateRunning.compareAndSet(false, true)) return
        executor.execute {
            try {
                val combined = StringBuilder()
                for (address in filterLists) {
                    val rules = downloadText(address) ?: continue
                    if (rules.isNotBlank()) {
                        combined.append("\n! Source: ").append(address).append('\n')
                        combined.append(rules).append('\n')
                    }
                    if (combined.length > MAX_FILTER_CHARS) break
                }

                if (combined.isNotEmpty() && combined.length <= MAX_FILTER_CHARS) {
                    val current = engine
                    if (current != null && current.loadFilterList(combined.toString())) {
                        try {
                            val resources = context.assets.open("adblock/resources.json")
                                .bufferedReader(Charsets.UTF_8).use { it.readText() }
                            current.loadResources(resources)
                        } catch (_: Exception) {
                        }
                        current.serializeTo(java.io.File(context.filesDir, "brave-adblock.bin"))
                        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            .edit().putLong(KEY_UPDATED_AT, System.currentTimeMillis()).apply()
                    }
                }
            } finally {
                updateRunning.set(false)
            }
        }
    }

    private fun downloadText(address: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "AdFreeTube/0.5 (Android WebView)")
                setRequestProperty("Accept", "text/plain,*/*")
            }
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(16 * 1024)
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    output.append(buffer, 0, count)
                    if (output.length > MAX_FILTER_CHARS) return null
                }
                output.toString()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    fun rememberPage(view: WebView, url: String?) {
        if (!url.isNullOrBlank()) pageUrls[view] = url
    }

    fun shouldBlock(view: WebView, request: WebResourceRequest): Boolean {
        val current = engine ?: return false
        val url = request.url.toString()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false

        val source = pageUrls[view]
            ?: request.requestHeaders["Referer"]
            ?: url
        val requestType = if (request.isForMainFrame) {
            "document"
        } else {
            RequestTypeMapper.from(request.url, request.requestHeaders["Accept"])
        }
        return current.checkNetworkRequest(url, source, requestType).matched
    }

    fun injectCosmetics(view: WebView, pageUrl: String?) {
        if (pageUrl.isNullOrBlank()) return
        rememberPage(view, pageUrl)
        val cosmetics = engine?.cosmeticResources(pageUrl) ?: return
        if (cosmetics.selectors.isEmpty() && cosmetics.js.isBlank()) return

        val css = JSONObject.quote(cosmetics.css())
        val scriptlets = JSONObject.quote(cosmetics.js)
        val js = """
            (function() {
              try {
                var cssText = $css;
                if (cssText) {
                  var style = document.createElement('style');
                  style.setAttribute('data-adfree-tube', 'cosmetic');
                  style.textContent = cssText;
                  (document.head || document.documentElement).appendChild(style);
                }
              } catch (e) {}
              try {
                var scriptlets = $scriptlets;
                if (scriptlets) (0, eval)(scriptlets);
              } catch (e) {}
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }

    fun emptyResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
