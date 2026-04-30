package com.videovault.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videovault.App
import com.videovault.data.local.database.entity.DownloadHistoryEntity
import com.videovault.data.model.DownloadTask
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DownloadsViewModel(app: Application) : AndroidViewModel(app) {
    private val dm = (app as App).downloadManager
    private val dao = (app as App).database.downloadHistoryDao()
    val activeDownloads: StateFlow<List<DownloadTask>> = dm.activeDownloads
    val completedDownloads: StateFlow<List<DownloadHistoryEntity>> = dao.getCompletedDownloads().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    private val _toast = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toast

    fun pauseDownload(id: String) = dm.pauseDownload(id)
    fun resumeDownload(id: String) = dm.resumeDownload(id)
    fun cancelDownload(id: String) = dm.cancelDownload(id)
    fun deleteDownload(id: String) { viewModelScope.launch { dm.cancelDownload(id); dao.deleteById(id) } }
}
