package com.videovault.data.local

import android.content.Context
import android.os.Environment
import com.videovault.data.model.*
import com.videovault.data.local.database.AppDatabase
import com.videovault.data.local.database.entity.DownloadHistoryEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DownloadManager(private val ctx: Context) {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    private val _active = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadTask>> = _active.asStateFlow()
    private val _progress = MutableSharedFlow<DownloadProgress>()
    val downloadProgress: SharedFlow<DownloadProgress> = _progress.asSharedFlow()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dao by lazy { AppDatabase.getInstance(ctx).downloadHistoryDao() }

    fun getDownloadDirectory(): File {
        val d = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)
            File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoVault")
        else File(ctx.filesDir, "downloads")
        if (!d.exists()) d.mkdirs(); return d
    }

    suspend fun startDownload(info: VideoInfo, variant: VideoVariant, taskId: String = UUID.randomUUID().toString()): String {
        val f = File(getDownloadDirectory(), "video_${info.tweetId}_${variant.getQualityLabel()}.mp4")
        val task = DownloadTask(taskId, info, variant, f.absolutePath, variant.url)
        dao.insert(DownloadHistoryEntity(taskId, info.tweetId, info.tweetUrl, info.authorName, info.authorUsername, info.tweetText, info.thumbnailUrl, variant.url, variant.getQualityLabel(), variant.bitrate, f.absolutePath, state = 1))
        _active.value += task
        jobs[taskId] = scope.launch { downloadFile(task, f) }
        return taskId
    }

    private suspend fun downloadFile(task: DownloadTask, out: File) {
        try {
            updateState(task.id, DownloadTaskState.DOWNLOADING)
            val req = Request.Builder().url(task.url).addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty body")
            val total = body.contentLength()
            task.totalBytes = total
            body.byteStream().use { inp ->
                FileOutputStream(out).use { outp ->
                    val buf = ByteArray(8192)
                    var read: Int; var done = 0L
                    while (inp.read(buf).also { read = it } != -1) {
                        if (jobs[task.id]?.isActive != true) { outp.flush(); updateState(task.id, DownloadTaskState.PAUSED); return }
                        outp.write(buf, 0, read); done += read
                        val pct = if (total > 0) ((done * 100) / total).toInt() else 0
                        task.downloadedBytes = done; task.progress = pct; task.state = DownloadTaskState.DOWNLOADING
                        _progress.emit(DownloadProgress(task.id, pct, done, total))
                        dao.updateProgress(task.id, 1, pct, out.absolutePath, done)
                    }
                }
            }
            task.state = DownloadTaskState.COMPLETED; task.completedAt = System.currentTimeMillis()
            updateState(task.id, DownloadTaskState.COMPLETED)
            dao.updateProgress(task.id, 2, 100, out.absolutePath, task.totalBytes)
            _progress.emit(DownloadProgress(task.id, 100, task.totalBytes, task.totalBytes, true, out.absolutePath))
        } catch (e: Exception) {
            task.state = DownloadTaskState.FAILED; updateState(task.id, DownloadTaskState.FAILED)
            _progress.emit(DownloadProgress(task.id, error = e.message))
        }
    }

    fun pauseDownload(id: String) { jobs[id]?.cancel(); jobs.remove(id); updateState(id, DownloadTaskState.PAUSED) }
    fun resumeDownload(id: String) {
        val t = _active.value.find { it.id == id } ?: return
        val f = File(t.outputPath); if (f.exists()) f.delete()
        scope.launch { jobs[id] = scope.launch { downloadFile(t, f) } }
    }
    fun cancelDownload(id: String) {
        jobs[id]?.cancel(); jobs.remove(id)
        _active.value.find { it.id == id }?.let { File(it.outputPath).delete() }
        _active.value = _active.value.filter { it.id != id }
        scope.launch { dao.deleteById(id) }
    }
    private fun updateState(id: String, s: DownloadTaskState) { _active.value = _active.value.map { if (it.id == id) it.copy(state = s) else it } }

    data class DownloadProgress(val taskId: String, val progress: Int = 0, val downloadedBytes: Long = 0, val totalBytes: Long = 0, val isCompleted: Boolean = false, val filePath: String? = null, val error: String? = null)
}
