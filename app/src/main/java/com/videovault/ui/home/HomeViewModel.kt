package com.videovault.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videovault.App
import com.videovault.data.model.*
import com.videovault.data.remote.api.CobaltApiService
import com.videovault.data.remote.repository.VideoParseState
import com.videovault.data.remote.repository.VideoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = VideoRepository()
    private val dm = (app as App).downloadManager

    private val _parseState = MutableStateFlow<VideoParseState>(VideoParseState.Idle)
    val parseState: StateFlow<VideoParseState> = _parseState
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState
    private val _videoInfo = MutableStateFlow<VideoInfo?>(null)
    val currentVideoInfo: StateFlow<VideoInfo?> = _videoInfo
    private val _toast = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toast

    fun parseUrl(url: String, quality: String = "1080") {
        viewModelScope.launch {
            _parseState.value = VideoParseState.Loading
            repo.parseVideoFromUrl(url, quality).fold(
                onSuccess = {
                    _videoInfo.value = it
                    _parseState.value = VideoParseState.Success(it)
                },
                onFailure = {
                    _parseState.value = VideoParseState.Error(it.message ?: "Failed")
                    _toast.emit(it.message ?: "Error")
                }
            )
        }
    }

    fun startDownload(variant: VideoVariant) {
        val info = _videoInfo.value ?: return
        viewModelScope.launch {
            _downloadState.value = DownloadState.Downloading("", 0, 0, 0)
            try {
                dm.startDownload(info, variant)
                _downloadState.value = DownloadState.Completed("")
                _toast.emit("Download started!")
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Failed")
                _toast.emit("Download failed: ${e.message}")
            }
        }
    }
}
