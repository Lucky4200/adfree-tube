package com.adfree.tube

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.ProgressHolder
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.TransformationException
import androidx.media3.transformer.Transformer
import java.io.File

@UnstableApi
object Media3Export {
    const val PREFS = "adfree_tube_media3_exports"
    const val KEY_FILES = "files"

    fun start(
        context: Context,
        url: String,
        audioOnly: Boolean,
        onStatus: (String) -> Unit
    ) {
        val appContext = context.applicationContext
        val outputDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            onStatus("Output folder create nahi ho saka.")
            return
        }

        val suffix = if (audioOnly) "m4a" else "mp4"
        val output = File(outputDir, "AdFreeTube_${System.currentTimeMillis()}.$suffix")
        val item = MediaItem.Builder().setUri(Uri.parse(url)).setMediaId(output.name).build()
        val edited = EditedMediaItem.Builder(item).setRemoveVideo(audioOnly).build()
        val handler = Handler(Looper.getMainLooper())
        val holder = ProgressHolder()
        lateinit var transformer: Transformer

        val progressTask = object : Runnable {
            override fun run() {
                try {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onStatus(if (audioOnly) "Audio extract ho raha hai… ${holder.progress}%" else "Video convert ho rahi hai… ${holder.progress}%")
                    }
                    handler.postDelayed(this, 700)
                } catch (_: Exception) {
                    // Completion and errors are reported by the Transformer listener.
                }
            }
        }

        val listener = object : Transformer.Listener {
            override fun onTransformationCompleted(inputMediaItem: MediaItem) {
                handler.removeCallbacks(progressTask)
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val files = prefs.getStringSet(KEY_FILES, emptySet())?.toMutableSet() ?: mutableSetOf()
                files.add(output.name)
                prefs.edit().putStringSet(KEY_FILES, files).apply()
                onStatus("Complete ✓  ${output.name}")
            }

            override fun onTransformationError(inputMediaItem: MediaItem, exception: TransformationException) {
                handler.removeCallbacks(progressTask)
                output.delete()
                onStatus("Export failed: ${exception.message ?: "Unsupported or protected stream"}")
            }
        }

        try {
            transformer = Transformer.Builder(appContext).addListener(listener).build()
            transformer.start(edited, output.absolutePath)
            onStatus(if (audioOnly) "Audio-only M4A export shuru ho gaya…" else "MP4 export shuru ho gaya…")
            handler.post(progressTask)
        } catch (e: Exception) {
            handler.removeCallbacks(progressTask)
            output.delete()
            onStatus("Export start nahi hua: ${e.message ?: "Unknown error"}")
        }
    }
}
