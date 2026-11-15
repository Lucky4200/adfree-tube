package com.adfree.tube

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/** Publishes completed media into Android's shared MediaStore so Gallery/music apps can see it. */
object MediaPublisher {
    data class Published(val name: String, val uri: String)

    fun publish(context: Context, source: File): Published {
        require(source.exists() && source.isFile) { "Downloaded file not found: ${source.name}" }
        val app = context.applicationContext
        val ext = source.extension.lowercase()
        val isAudio = ext in setOf("mp3", "m4a", "aac", "wav", "ogg", "opus", "flac")
        val mime = when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> if (isAudio) "audio/webm" else "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "flac" -> "audio/flac"
            else -> if (isAudio) "audio/*" else "video/*"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isAudio) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relative = if (isAudio) Environment.DIRECTORY_MUSIC + "/AdFree Tube"
                else Environment.DIRECTORY_MOVIES + "/AdFree Tube"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relative)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = app.contentResolver.insert(collection, values)
                ?: error("Android Gallery media entry create nahi kar saka")
            try {
                app.contentResolver.openOutputStream(uri, "w")!!.use { output ->
                    FileInputStream(source).use { input -> input.copyTo(output, 1024 * 1024) }
                }
                val ready = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                app.contentResolver.update(uri, ready, null, null)
                return Published(source.name, uri.toString())
            } catch (e: Exception) {
                app.contentResolver.delete(uri, null, null)
                throw e
            }
        }

        val folder = File(
            Environment.getExternalStoragePublicDirectory(if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES),
            "AdFree Tube"
        )
        if (!folder.exists() && !folder.mkdirs()) error("Public media folder create nahi hua")
        val target = File(folder, source.name)
        FileInputStream(source).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        MediaScannerConnection.scanFile(app, arrayOf(target.absolutePath), arrayOf(mime), null)
        return Published(target.name, Uri.fromFile(target).toString())
    }
}
