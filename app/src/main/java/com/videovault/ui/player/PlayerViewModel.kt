package com.videovault.ui.player

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel : ViewModel() {
    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked
    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    fun toggleLock() { _isLocked.value = !_isLocked.value }
    fun unlock() { _isLocked.value = false }
    fun setPlaybackSpeed(s: Float) { _playbackSpeed.value = s }
    fun updatePlayingState(p: Boolean) { _isPlaying.value = p }
    fun updatePosition(p: Long) { _currentPosition.value = p }
    fun updateDuration(d: Long) { _duration.value = d }

    companion object {
        val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)
    }
}
