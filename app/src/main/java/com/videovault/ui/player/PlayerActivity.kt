package com.videovault.ui.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.videovault.R
import com.videovault.data.model.VideoInfo
import com.videovault.databinding.ActivityPlayerBinding
import com.videovault.util.FileUtils
import com.videovault.util.PlaybackHistoryManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPlayerBinding
    private val vm: PlayerViewModel by viewModels()
    private val historyMgr by lazy { PlaybackHistoryManager.getInstance(this) }
    private var player: ExoPlayer? = null
    private var playWhenReady = true
    private var playbackPos = 0L
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            player?.let {
                binding.tvCurrentTime.text = FileUtils.formatDuration(it.currentPosition)
                binding.seekBar.progress = it.currentPosition.toInt()
                vm.updatePosition(it.currentPosition)
            }
            updateHandler.postDelayed(this, 500)
        }
    }

    private var gestureDetector: GestureDetector? = null
    private var initialTouchX = 0f; private var initialTouchY = 0f
    private var currentVolume = 1f; private var currentBrightness = 0.5f
    private var isSeeking = false

    private var videoUrl: String? = null
    private var videoInfo: VideoInfo? = null
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        videoInfo = intent.getParcelableExtra(EXTRA_VIDEO_INFO)
        isStreaming = intent.getBooleanExtra(EXTRA_IS_STREAMING, false)
        playbackPos = intent.getLongExtra(EXTRA_POSITION, 0L)
        setupUI(); setupGestures(); observeVM()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlayPause.setOnClickListener { player?.let { if (it.isPlaying) it.pause() else it.play() } }
        binding.btnLock.setOnClickListener { vm.toggleLock() }
        binding.btnUnlock.setOnClickListener { vm.unlock() }
        binding.btnSpeed.setOnClickListener { showSpeedMenu(it) }
        binding.btnFullscreen.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        binding.seekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) binding.tvCurrentTime.text = FileUtils.formatDuration(p.toLong()) }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) { isSeeking = true }
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) { isSeeking = false; player?.seekTo(sb?.progress?.toLong() ?: 0) }
        })
        videoInfo?.let { binding.tvVideoTitle.text = it.authorName; binding.tvVideoAuthor.text = "@${it.authorUsername}" }
        val lp = window.attributes; lp.screenBrightness = currentBrightness; window.attributes = lp
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { toggleControls(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                player?.let {
                    val w = binding.playerView.width
                    if (e.x < w / 2) { it.seekTo(maxOf(0, it.currentPosition - 10000)); showSeekFeedback(-10000) }
                    else { it.seekTo(minOf(it.duration, it.currentPosition + 10000)); showSeekFeedback(10000) }
                }; return true
            }
        })
        binding.playerView.setOnTouchListener { _, ev ->
            if (vm.isLocked.value) { binding.btnUnlock.visibility = View.VISIBLE; return@setOnTouchListener true }
            gestureDetector?.onTouchEvent(ev)
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { initialTouchX = ev.x; initialTouchY = ev.y; currentVolume = player?.volume ?: 1f }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.x - initialTouchX; val dy = ev.y - initialTouchY
                    if (abs(dx) > 50 && abs(dx) > abs(dy) && !isSeeking) {
                        isSeeking = true
                        val d = ((dx / binding.playerView.width) * (player?.duration ?: 0) / 10).toLong()
                        player?.seekTo((player?.currentPosition ?: 0) + d); showSeekFeedback(d)
                    } else if (abs(dy) > 50 && abs(dy) > abs(dx)) {
                        val third = binding.playerView.width / 3
                        if (initialTouchX < third) {
                            currentBrightness = (currentBrightness + (-dy / binding.playerView.height)).coerceIn(0.1f, 1f)
                            val lp = window.attributes; lp.screenBrightness = currentBrightness; window.attributes = lp
                            showBrightnessIndicator()
                        } else if (initialTouchX > third * 2) {
                            currentVolume = (currentVolume + (-dy / binding.playerView.height)).coerceIn(0f, 1f)
                            player?.volume = currentVolume; showVolumeIndicator()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isSeeking = false
            }; true
        }
        binding.btnUnlock.setOnTouchListener { _, _ -> vm.unlock(); binding.btnUnlock.visibility = View.GONE; true }
    }

    private fun showSpeedMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        PlayerViewModel.PLAYBACK_SPEEDS.forEach { popup.menu.add("${it}x") }
        popup.setOnMenuItemClickListener { item ->
            val s = item.title.toString().replace("x", "").toFloatOrNull() ?: 1f
            vm.setPlaybackSpeed(s); player?.setPlaybackSpeed(s); binding.tvSpeed.text = "${s}x"; true
        }; popup.show()
    }

    private fun toggleControls() {
        if (vm.isLocked.value) { binding.btnUnlock.visibility = View.VISIBLE; return }
        val show = binding.controlsOverlay.visibility != View.VISIBLE
        binding.controlsOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.topControls.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showSeekFeedback(d: Long) {
        binding.tvSeekFeedback.visibility = View.VISIBLE
        binding.tvSeekFeedback.text = if (d > 0) "+${d / 1000}s" else "${d / 1000}s"
        binding.tvSeekFeedback.postDelayed({ binding.tvSeekFeedback.visibility = View.GONE }, 500)
    }
    private fun showVolumeIndicator() { binding.volumeIndicator.visibility = View.VISIBLE; binding.volumeIndicator.progress = (currentVolume * 100).toInt(); binding.volumeIndicator.postDelayed({ binding.volumeIndicator.visibility = View.GONE }, 500) }
    private fun showBrightnessIndicator() { binding.brightnessIndicator.visibility = View.VISIBLE; binding.brightnessIndicator.progress = (currentBrightness * 100).toInt(); binding.brightnessIndicator.postDelayed({ binding.brightnessIndicator.visibility = View.GONE }, 500) }

    private fun observeVM() {
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.isLocked.collectLatest { lck -> binding.controlsOverlay.visibility = if (lck) View.GONE else View.VISIBLE; binding.btnLock.visibility = if (lck) View.GONE else View.VISIBLE; binding.btnUnlock.visibility = if (lck) View.VISIBLE else View.GONE }}
            launch { vm.playbackSpeed.collectLatest { player?.setPlaybackSpeed(it) }}
            launch { vm.isPlaying.collectLatest { binding.btnPlayPause.setImageResource(if (it) R.drawable.ic_pause else R.drawable.ic_play) }}
        }}
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build().also { p ->
            binding.playerView.player = p
            videoUrl?.let { url -> p.setMediaItem(MediaItem.fromUri(url.toUri())); p.playWhenReady = playWhenReady; p.prepare(); if (playbackPos > 0) p.seekTo(playbackPos) }
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> { binding.progressBar.visibility = View.GONE; binding.seekBar.max = p.duration.toInt(); binding.tvDuration.text = FileUtils.formatDuration(p.duration); vm.updateDuration(p.duration); videoUrl?.let { historyMgr.addHistory(videoInfo?.authorName ?: "Video", it, p.duration) }}
                        Player.STATE_BUFFERING -> binding.progressBar.visibility = View.VISIBLE
                        Player.STATE_ENDED -> vm.updatePlayingState(false)
                        else -> {}
                    }
                }
                override fun onIsPlayingChanged(playing: Boolean) { vm.updatePlayingState(playing) }
            })
        }; updateHandler.post(updateRunnable)
    }

    private fun releasePlayer() {
        updateHandler.removeCallbacks(updateRunnable)
        player?.let { playbackPos = it.currentPosition; playWhenReady = it.playWhenReady; it.release() }; player = null
    }

    override fun onStart() { super.onStart(); initPlayer() }
    override fun onResume() { super.onResume(); hideSystemUI() }
    override fun onPause() { super.onPause(); player?.pause() }
    override fun onStop() { super.onStop(); releasePlayer() }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).let { it.hide(WindowInsetsCompat.Type.systemBars()); it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE }
    }

    companion object {
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_VIDEO_INFO = "video_info"
        const val EXTRA_IS_STREAMING = "is_streaming"
        const val EXTRA_POSITION = "position"
    }
}
