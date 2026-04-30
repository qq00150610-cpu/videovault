package com.videovault.ui.downloads

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.videovault.R
import com.videovault.data.local.database.entity.DownloadHistoryEntity
import com.videovault.data.model.DownloadTask
import com.videovault.data.model.DownloadTaskState
import com.videovault.databinding.FragmentDownloadsBinding
import com.videovault.databinding.ItemActiveDownloadBinding
import com.videovault.databinding.ItemDownloadHistoryBinding
import com.videovault.ui.player.PlayerActivity
import com.videovault.util.FileUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DownloadsFragment : Fragment() {
    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!
    private val vm: DownloadsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activeAdapter = ActiveAdapter({ vm.pauseDownload(it.id) }, { vm.resumeDownload(it.id) }, { vm.cancelDownload(it.id) })
        val histAdapter = HistoryAdapter(
            { it.filePath?.let { p -> if (FileUtils.fileExists(p)) startActivity(Intent(requireContext(), PlayerActivity::class.java).apply { putExtra(PlayerActivity.EXTRA_VIDEO_URL, p) }) else Snackbar.make(binding.root, R.string.file_not_found, Snackbar.LENGTH_SHORT).show() } },
            { vm.deleteDownload(it.id) },
            { it.filePath?.let { p -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, android.net.Uri.parse(p)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, getString(R.string.share_video))) } }
        )
        binding.rvActiveDownloads.layoutManager = LinearLayoutManager(context); binding.rvActiveDownloads.adapter = activeAdapter
        binding.rvHistory.layoutManager = LinearLayoutManager(context); binding.rvHistory.adapter = histAdapter
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.activeDownloads.collectLatest { activeAdapter.submitList(it); binding.tvActiveEmpty.isVisible = it.isEmpty(); binding.rvActiveDownloads.isVisible = it.isNotEmpty() }}
            launch { vm.completedDownloads.collectLatest { histAdapter.submitList(it); binding.tvHistoryEmpty.isVisible = it.isEmpty(); binding.rvHistory.isVisible = it.isNotEmpty() }}
            launch { vm.toastMessage.collectLatest { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() }}
        }}
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ActiveAdapter(private val onPause: (DownloadTask) -> Unit, private val onResume: (DownloadTask) -> Unit, private val onCancel: (DownloadTask) -> Unit) :
    ListAdapter<DownloadTask, ActiveAdapter.VH>(object : DiffUtil.ItemCallback<DownloadTask>() { override fun areItemsTheSame(a: DownloadTask, b: DownloadTask) = a.id == b.id; override fun areContentsTheSame(a: DownloadTask, b: DownloadTask) = a == b }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemActiveDownloadBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(h: VH, pos: Int) { h.bind(getItem(pos)) }
    inner class VH(private val b: ItemActiveDownloadBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(t: DownloadTask) {
            b.tvAuthor.text = t.videoInfo.authorName; b.tvQuality.text = t.variant.getQualityLabel(); b.tvProgress.text = "${t.progress}%"; b.progressBar.progress = t.progress
            when (t.state) { DownloadTaskState.DOWNLOADING -> { b.btnPauseResume.setImageResource(android.R.drawable.ic_media_pause); b.btnPauseResume.setOnClickListener { onPause(t) } }; DownloadTaskState.PAUSED -> { b.btnPauseResume.setImageResource(android.R.drawable.ic_media_play); b.btnPauseResume.setOnClickListener { onResume(t) } }; else -> {} }
            b.btnCancel.setOnClickListener { onCancel(t) }
        }
    }
}

class HistoryAdapter(private val onPlay: (DownloadHistoryEntity) -> Unit, private val onDelete: (DownloadHistoryEntity) -> Unit, private val onShare: (DownloadHistoryEntity) -> Unit) :
    ListAdapter<DownloadHistoryEntity, HistoryAdapter.VH>(object : DiffUtil.ItemCallback<DownloadHistoryEntity>() { override fun areItemsTheSame(a: DownloadHistoryEntity, b: DownloadHistoryEntity) = a.id == b.id; override fun areContentsTheSame(a: DownloadHistoryEntity, b: DownloadHistoryEntity) = a == b }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemDownloadHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(h: VH, pos: Int) { h.bind(getItem(pos)) }
    inner class VH(private val b: ItemDownloadHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(i: DownloadHistoryEntity) {
            b.tvAuthor.text = i.authorName; b.tvUsername.text = "@${i.authorUsername}"; b.tvQuality.text = i.quality; b.tvFileSize.text = if (i.fileSize > 0) FileUtils.formatFileSize(i.fileSize) else ""
            b.btnPlay.setOnClickListener { onPlay(i) }; b.btnShare.setOnClickListener { onShare(i) }; b.btnDelete.setOnClickListener { onDelete(i) }
        }
    }
}
