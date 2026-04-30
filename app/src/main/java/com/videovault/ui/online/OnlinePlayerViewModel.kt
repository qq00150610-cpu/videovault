package com.videovault.ui.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.videovault.util.PlaybackHistoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class RecentUrl(val url: String, val title: String, val timestamp: Long)

class OnlinePlayerViewModel(app: Application) : AndroidViewModel(app) {
    private val mgr = PlaybackHistoryManager.getInstance(app)
    private val _recentUrls = MutableStateFlow<List<RecentUrl>>(emptyList())
    val recentUrls: StateFlow<List<RecentUrl>> = _recentUrls

    init { loadHistory() }

    private fun loadHistory() {
        _recentUrls.value = mgr.getHistory().map { RecentUrl(it.uri, it.title, it.timestamp) }
    }

    fun addToHistory(url: String) {
        mgr.addHistory(url.substringAfterLast("/").take(50), url)
        loadHistory()
    }

    fun removeFromHistory(url: String) {
        val h = mgr.getHistory().find { it.uri == url }
        h?.let { mgr.clearHistory(); loadHistory() }
    }

    fun clearHistory() { mgr.clearHistory(); _recentUrls.value = emptyList() }
}
