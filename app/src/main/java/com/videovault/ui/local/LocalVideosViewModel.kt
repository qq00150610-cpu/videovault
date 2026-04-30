package com.videovault.ui.local

import android.app.Application
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videovault.util.FileUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class LocalVideo(val id: Long, val name: String, val path: String, val uri: Uri, val size: Long, val duration: Long, val width: Int, val height: Int)
enum class SortOrder { DATE_DESC, DATE_ASC, SIZE_DESC, SIZE_ASC, NAME_ASC, NAME_DESC }

class LocalVideosViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _videos = MutableStateFlow<List<LocalVideo>>(emptyList())
    val videos: StateFlow<List<LocalVideo>> = _videos
    private val _loading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _loading
    private val _toast = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toast
    private var sort = SortOrder.DATE_DESC

    init { loadVideos() }

    fun loadVideos() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val list = mutableListOf<LocalVideo>()
                val dir = FileUtils.getVideoDirectory(app)
                dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in listOf("mp4", "mkv", "webm", "3gp") }?.forEach { f ->
                    list.add(LocalVideo(f.hashCode().toLong(), f.name, f.absolutePath, Uri.fromFile(f), f.length(), 0, 0, 0))
                }
                val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATA, MediaStore.Video.Media.SIZE, MediaStore.Video.Media.DURATION, MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT)
                app.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj, null, null, null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val wCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                    val hCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol); val name = c.getString(nameCol); val path = c.getString(dataCol)
                        val size = c.getLong(sizeCol); val dur = c.getLong(durCol); val w = c.getInt(wCol); val h = c.getInt(hCol)
                        if (list.none { it.path == path }) list.add(LocalVideo(id, name ?: "video", path, Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id.toString()), size, dur, w, h))
                    }
                }
                _videos.value = sortList(list)
            } catch (e: Exception) { _toast.emit("Error: ${e.message}") }
            _loading.value = false
        }
    }

    fun setSortOrder(s: SortOrder) { sort = s; _videos.value = sortList(_videos.value) }
    fun deleteVideo(v: LocalVideo) { FileUtils.deleteFile(v.path); _videos.value = _videos.value.filter { it.id != v.id } }

    private fun sortList(list: List<LocalVideo>) = when (sort) {
        SortOrder.DATE_DESC -> list.sortedByDescending { File(it.path).lastModified() }
        SortOrder.DATE_ASC -> list.sortedBy { File(it.path).lastModified() }
        SortOrder.SIZE_DESC -> list.sortedByDescending { it.size }
        SortOrder.SIZE_ASC -> list.sortedBy { it.size }
        SortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
        SortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
    }
}
