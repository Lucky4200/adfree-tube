package com.adfree.tube

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Local-first yt-dlp engine with bundled Python, QuickJS and FFmpeg. */
object YtDlpEngine {
    private const val PREFS = "adfree_tube_ytdlp_exports"
    private const val KEY_FILES = "files"
    const val KEY_PUBLISHED = "published_media"
    private const val KEY_LAST_NIGHTLY_UPDATE = "last_ytdlp_nightly_update"
    private const val NIGHTLY_UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L

    private val initializing = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var ready = false
    @Volatile private var initError: String? = null

    fun initialize(context: Context) {
        if (ready || !initializing.compareAndSet(false, true)) return
        executor.execute {
            val app = context.applicationContext
            try {
                YoutubeDL.getInstance().init(app)
                FFmpeg.getInstance().init(app)

                // Keep the embedded extractor current. YouTube frequently changes its
                // player/API; NIGHTLY is the yt-dlp-recommended channel for regular use.
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val lastUpdate = prefs.getLong(KEY_LAST_NIGHTLY_UPDATE, 0L)
                if (System.currentTimeMillis() - lastUpdate >= NIGHTLY_UPDATE_INTERVAL_MS) {
                    try {
                        YoutubeDL.getInstance().updateYoutubeDL(
                            app,
                            YoutubeDL.UpdateChannel.NIGHTLY
                        )
                        prefs.edit()
                            .putLong(KEY_LAST_NIGHTLY_UPDATE, System.currentTimeMillis())
                            .apply()
                        initError = null
                    } catch (updateException: Exception) {
                        // Do not disable downloads when the phone is offline or GitHub is
                        // temporarily unavailable; retain the bundled/current extractor.
                        initError = "yt-dlp update check failed: " +
                            (updateException.message ?: "network unavailable")
                    }
                }
                ready = true
            } catch (e: Exception) {
                initError = e.message ?: "Extractor initialization failed"
            } finally {
                initializing.set(false)
            }
        }
    }

    fun awaitReady(timeoutMs: Long = 180000L): Boolean {
        val until = System.currentTimeMillis() + timeoutMs
        while (!ready && initializing.get() && System.currentTimeMillis() < until) {
            Thread.sleep(250)
        }
        return ready
    }

    fun initializationError(): String? = initError

    fun extract(url: String): VideoInfo {
        check(awaitReady()) {
            initializationError() ?: "Downloader engine is still initializing."
        }
        return YoutubeDL.getInstance().getInfo(url)
    }

    fun download(
        context: Context,
        url: String,
        selector: String,
        audioOnly: Boolean,
        onProgress: (Float, Long, String) -> Unit
    ): String {
        check(awaitReady()) {
            initializationError() ?: "Downloader engine is not ready."
        }

        val dir = downloadDirectory(context)
        if (!dir.exists() && !dir.mkdirs()) error("Download folder could not be created")
        val before = dir.listFiles()?.map { it.name }?.toSet().orEmpty()

        try {
            YoutubeDL.getInstance().execute(
                buildDownloadRequest(url, selector, audioOnly, dir, useAndroidClient = false),
                UUID.randomUUID().toString()
            ) { progress, eta, line ->
                onProgress(progress, eta, line)
            }
        } catch (firstError: Exception) {
            val is403 = firstError.message.orEmpty().contains("403", ignoreCase = true)
            if (!isYouTubeUrl(url) || !is403) throw firstError

            // Fallback for YouTube's intermittent SABR / GVS 403 responses.
            onProgress(0f, 0L, "YouTube 403 detected; retrying with alternate Android player...")
            YoutubeDL.getInstance().execute(
                buildDownloadRequest(url, selector, audioOnly, dir, useAndroidClient = true),
                UUID.randomUUID().toString()
            ) { progress, eta, line ->
                onProgress(progress, eta, line)
            }
        }

        val created = dir.listFiles()
            ?.filter { it.isFile && it.name !in before }
            ?.filter { it.extension.lowercase() in setOf("mp4", "m4v", "webm", "mov", "mp3", "m4a", "aac", "wav", "ogg", "opus", "flac") }
            ?.map { it.name }
            .orEmpty()

        if (created.isNotEmpty()) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getStringSet(KEY_FILES, emptySet()).orEmpty().toMutableSet()
            saved.addAll(created)
            prefs.edit().putStringSet(KEY_FILES, saved).apply()
        }

        // Copy finished video/audio into Android's shared media library.
        val published = mutableListOf<MediaPublisher.Published>()
        created.forEach { name ->
            val source = File(dir, name)
            runCatching { MediaPublisher.publish(context, source) }
                .onSuccess { published.add(it) }
                .onFailure { onProgress(100f, 0L, "Download complete; Gallery copy failed: " + (it.message ?: "unknown error")) }
        }
        if (published.isNotEmpty()) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val entries = prefs.getStringSet(KEY_PUBLISHED, emptySet()).orEmpty().toMutableSet()
            published.forEach { entries.add(it.name + "|" + it.uri) }
            prefs.edit().putStringSet(KEY_PUBLISHED, entries).apply()
        }
        return published.lastOrNull()?.name ?: created.lastOrNull() ?: "Download completed. Check Downloads tab."
    }

    private fun buildDownloadRequest(
        url: String,
        selector: String,
        audioOnly: Boolean,
        dir: File,
        useAndroidClient: Boolean
    ): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
            .addOption("-f", selector)
            .addOption("-o", File(dir, "%(title).120B [%(id)s].%(ext)s").absolutePath)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--newline")
            .addOption("--concurrent-fragments", "8")

        if (useAndroidClient && isYouTubeUrl(url)) {
            request.addOption("--extractor-args", "youtube:player_client=android")
        }
        if (audioOnly) {
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
        }
        return request
    }

    private fun isYouTubeUrl(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
        return host == "youtu.be" ||
            host == "youtube.com" ||
            host.endsWith(".youtube.com") ||
            host.endsWith(".youtube-nocookie.com")
    }

    fun downloadDirectory(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "AdFreeTube")
    }
}
