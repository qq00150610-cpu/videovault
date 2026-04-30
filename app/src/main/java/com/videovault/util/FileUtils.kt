package com.videovault.util

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.text.DecimalFormat

object FileUtils {
    fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val g = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, g.toDouble())) + " " + units[g]
    }

    fun formatDuration(ms: Long): String {
        val s = (ms / 1000) % 60; val m = (ms / 60000) % 60; val h = ms / 3600000
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    fun getFileExtension(url: String) = MimeTypeMap.getFileExtensionFromUrl(url) ?: "mp4"
    fun getMimeType(url: String) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(getFileExtension(url)) ?: "video/mp4"
    fun getVideoDirectory(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoVault")
        if (!d.exists()) d.mkdirs(); return d
    }
    fun deleteFile(p: String) = try { File(p).delete() } catch (_: Exception) { false }
    fun fileExists(p: String) = File(p).exists()
    fun getFileSize(p: String) = try { File(p).length() } catch (_: Exception) { 0L }
    fun getFileName(p: String) = File(p).name

    fun scanMediaStore(ctx: Context, f: File) {
        try {
            val v = android.content.ContentValues().apply {
                put(MediaStore.Video.Media.DATA, f.absolutePath)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            }
            ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
        } catch (_: Exception) {}
    }
}
