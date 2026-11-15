package com.adfree.tube

import android.content.Context
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
    private val initializing = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var ready = false
    @Volatile private var initError: String? = null

    fun initialize(context: Context) {
        if (ready || !initializing.compareAndSet(false, true)) return
        executor.execute {
            try {
                YoutubeDL.getInstance().init(context.applicationContext)
                FFmpeg.getInstance().init(context.applicationContext)
                ready = true
                initError = null
            } catch (e: Exception) {
                initError = e.message ?: "Extractor initialization failed"
            } finally {
                initializing.set(false)
            }
        }
    }

    fun awaitReady(timeoutMs: Long = 120000L): Boolean {
        val until = System.currentTimeMillis() + timeoutMs
        while (!ready && initializing.get() && System.currentTimeMillis() < until) Thread.sleep(250)
        return ready
    }

    fun initializationError(): String? = initError

    fun extract(url: String): VideoInfo {
        check(awaitReady()) { initializationError() ?: "Downloader engine is still initializing." }
        return YoutubeDL.getInstance().getInfo(url)
    }

    fun download(context: Context, url: String, selector: String, audioOnly: Boolean,
                 onProgress: (Float, Long, String) -> Unit): String {
        check(awaitReady()) { initializationError() ?: "Downloader engine is not ready." }
        val dir = downloadDirectory(context)
        if (!dir.exists() && !dir.mkdirs()) error("Download folder could not be created")
        val request = YoutubeDLRequest(url)
            .addOption("-f", selector)
            .addOption("-o", File(dir, "%(title).120B [%(id)s].%(ext)s").absolutePath)
            .addOption("--no-playlist")
            .addOption("--no-mtime")
            .addOption("--newline")
            .addOption("--concurrent-fragments", "8")
        if (audioOnly) {
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
        }
        val before = dir.listFiles()?.map { it.name }?.toSet().orEmpty()
        YoutubeDL.getInstance().execute(request, UUID.randomUUID().toString()) { progress, eta, line ->
            onProgress(progress, eta, line)
        }
        val created = dir.listFiles()?.filter { it.isFile && it.name !in before }?.map { it.name }.orEmpty()
        if (created.isNotEmpty()) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getStringSet(KEY_FILES, emptySet()).orEmpty().toMutableSet()
            saved.addAll(created)
            prefs.edit().putStringSet(KEY_FILES, saved).apply()
        }
        return created.lastOrNull() ?: "Download completed. Check Downloads tab."
    }

    fun downloadDirectory(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "AdFreeTube")
    }
}
