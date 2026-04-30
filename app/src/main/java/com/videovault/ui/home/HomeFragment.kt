package com.videovault.ui.home

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.snackbar.Snackbar
import com.videovault.R
import com.videovault.data.model.*
import com.videovault.data.remote.repository.VideoParseState
import com.videovault.databinding.FragmentHomeBinding
import com.videovault.ui.player.PlayerActivity
import com.videovault.util.FileUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val vm: HomeViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPaste.setOnClickListener {
            val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cb.primaryClip?.getItemAt(0)?.text?.toString()
            if (!t.isNullOrEmpty()) binding.etUrl.setText(t) else Snackbar.make(binding.root, R.string.clipboard_empty, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnParse.setOnClickListener {
            val u = binding.etUrl.text.toString()
            if (u.isNotEmpty()) vm.parseUrl(u) else Snackbar.make(binding.root, R.string.please_enter_url, Snackbar.LENGTH_SHORT).show()
        }
        observeState()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.parseState.collectLatest { s ->
                    binding.progressBar.isVisible = s is VideoParseState.Loading
                    binding.cardResult.isVisible = s is VideoParseState.Success
                    binding.tvError.isVisible = s is VideoParseState.Error
                    binding.btnParse.isEnabled = s !is VideoParseState.Loading
                    if (s is VideoParseState.Error) binding.tvError.text = s.message
                }}
                launch { vm.currentVideoInfo.collectLatest { it?.let { info -> updateInfo(info) } }}
                launch { vm.downloadState.collectLatest { s ->
                    when (s) {
                        is DownloadState.Downloading -> { binding.downloadProgressLayout.isVisible = true; binding.progressDownload.progress = s.progress; binding.tvProgress.text = "${s.progress}%" }
                        is DownloadState.Completed -> { binding.downloadProgressLayout.isVisible = false; Snackbar.make(binding.root, R.string.download_completed, Snackbar.LENGTH_SHORT).show() }
                        is DownloadState.Error -> { binding.downloadProgressLayout.isVisible = false; Snackbar.make(binding.root, s.message, Snackbar.LENGTH_SHORT).show() }
                        else -> binding.downloadProgressLayout.isVisible = false
                    }
                }}
                launch { vm.toastMessage.collectLatest { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() }}
            }
        }
    }

    private fun updateInfo(info: VideoInfo) {
        binding.apply {
            info.thumbnailUrl?.let { ivThumbnail.load(it) { crossfade(true); placeholder(R.drawable.ic_video_placeholder); error(R.drawable.ic_video_placeholder) } }
            tvAuthor.text = info.authorName; tvUsername.text = "@${info.authorUsername}"; tvTweetText.text = info.tweetText
            val q = info.getAvailableQualities()
            val labels = q.map { "${it.getQualityLabel()} (${FileUtils.formatFileSize(it.bitrate.toLong() * 60)})" }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, labels)
            spinnerQuality.setAdapter(adapter); spinnerQuality.setText(labels.firstOrNull() ?: "", false)
            btnDownload.setOnClickListener {
                val idx = q.indexOfFirst { it.getQualityLabel() == spinnerQuality.text.toString().split(" ").first() }
                if (idx >= 0) vm.startDownload(q[idx])
            }
            btnPlayOnline.setOnClickListener {
                val idx = q.indexOfFirst { it.getQualityLabel() == spinnerQuality.text.toString().split(" ").first() }
                if (idx >= 0) {
                    startActivity(Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra(PlayerActivity.EXTRA_VIDEO_URL, q[idx].url)
                        putExtra(PlayerActivity.EXTRA_VIDEO_INFO, info)
                        putExtra(PlayerActivity.EXTRA_IS_STREAMING, true)
                    })
                }
            }
            btnDownloadGif.visibility = if (info.hasGif()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
