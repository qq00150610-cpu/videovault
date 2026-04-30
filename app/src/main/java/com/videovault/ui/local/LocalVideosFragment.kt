package com.videovault.ui.local

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
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
import coil.load
import coil.decode.VideoFrameDecoder
import com.google.android.material.snackbar.Snackbar
import com.videovault.R
import com.videovault.data.model.PlaybackHistory
import com.videovault.databinding.FragmentLocalVideosBinding
import com.videovault.databinding.ItemLocalVideoBinding
import com.videovault.databinding.ItemPlaybackHistoryBinding
import com.videovault.ui.player.PlayerActivity
import com.videovault.util.FileUtils
import com.videovault.util.PlaybackHistoryManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class LocalVideosFragment : Fragment() {
    private var _binding: FragmentLocalVideosBinding? = null
    private val binding get() = _binding!!
    private val vm: LocalVideosViewModel by viewModels()
    private lateinit var histMgr: PlaybackHistoryManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocalVideosBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        histMgr = PlaybackHistoryManager.getInstance(requireContext())
        val adapter = LocalVideoAdapter({ openPlayer(it.path) }, { v, anchor -> showPopup(v, anchor) })
        binding.rvVideos.layoutManager = LinearLayoutManager(context); binding.rvVideos.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener { vm.loadVideos() }
        binding.btnSort.setOnClickListener { showSortMenu(it) }
        setupHistory()
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.videos.collectLatest { adapter.submitList(it); binding.tvEmpty.isVisible = it.isEmpty(); binding.rvVideos.isVisible = it.isNotEmpty() }}
            launch { vm.isLoading.collectLatest { binding.swipeRefresh.isRefreshing = it }}
            launch { vm.toastMessage.collectLatest { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() }}
        }}
    }

    override fun onResume() { super.onResume(); vm.loadVideos(); setupHistory() }

    private fun setupHistory() {
        val hAdapter = HistoryAdapter { openPlayer(it.uri) }
        binding.rvHistory.layoutManager = LinearLayoutManager(context); binding.rvHistory.adapter = hAdapter
        val list = histMgr.getHistory()
        hAdapter.submitList(list); binding.tvHistoryEmpty.isVisible = list.isEmpty(); binding.rvHistory.isVisible = list.isNotEmpty()
        binding.btnClearHistory.setOnClickListener { histMgr.clearHistory(); setupHistory(); Snackbar.make(binding.root, "History cleared", Snackbar.LENGTH_SHORT).show() }
    }

    private fun showSortMenu(anchor: View) {
        val p = PopupMenu(requireContext(), anchor)
        p.menu.add(0, 1, 0, R.string.sort_date_newest); p.menu.add(0, 2, 0, R.string.sort_date_oldest); p.menu.add(0, 3, 0, R.string.sort_size_largest); p.menu.add(0, 4, 0, R.string.sort_size_smallest); p.menu.add(0, 5, 0, R.string.sort_name_az); p.menu.add(0, 6, 0, R.string.sort_name_za)
        p.setOnMenuItemClickListener { vm.setSortOrder(when (it.itemId) { 1 -> SortOrder.DATE_DESC; 2 -> SortOrder.DATE_ASC; 3 -> SortOrder.SIZE_DESC; 4 -> SortOrder.SIZE_ASC; 5 -> SortOrder.NAME_ASC; 6 -> SortOrder.NAME_DESC; else -> SortOrder.DATE_DESC }); true }
        p.show()
    }

    private fun showPopup(v: LocalVideo, anchor: View) {
        val p = PopupMenu(requireContext(), anchor)
        p.menu.add(0, 1, 0, R.string.btn_play); p.menu.add(0, 2, 0, R.string.share_video); p.menu.add(0, 3, 0, R.string.video_details)
        p.setOnMenuItemClickListener { when (it.itemId) { 1 -> openPlayer(v.path); 2 -> shareVideo(v); 3 -> showDetails(v) }; true }; p.show()
    }

    private fun openPlayer(path: String) { startActivity(Intent(requireContext(), PlayerActivity::class.java).apply { putExtra(PlayerActivity.EXTRA_VIDEO_URL, path); putExtra(PlayerActivity.EXTRA_IS_STREAMING, false) }) }
    private fun openPlayer(uri: String) { startActivity(Intent(requireContext(), PlayerActivity::class.java).apply { putExtra(PlayerActivity.EXTRA_VIDEO_URL, uri); putExtra(PlayerActivity.EXTRA_IS_STREAMING, true) }) }
    private fun shareVideo(v: LocalVideo) { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, v.uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, getString(R.string.share_video))) }
    private fun showDetails(v: LocalVideo) { Snackbar.make(binding.root, "${v.name}\n${FileUtils.formatFileSize(v.size)}\n${v.width}x${v.height}", Snackbar.LENGTH_LONG).show() }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class LocalVideoAdapter(private val onPlay: (LocalVideo) -> Unit, private val onMore: (LocalVideo, View) -> Unit) :
    ListAdapter<LocalVideo, LocalVideoAdapter.VH>(object : DiffUtil.ItemCallback<LocalVideo>() { override fun areItemsTheSame(a: LocalVideo, b: LocalVideo) = a.id == b.id; override fun areContentsTheSame(a: LocalVideo, b: LocalVideo) = a == b }) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemLocalVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(h: VH, pos: Int) { h.bind(getItem(pos)) }
    inner class VH(private val b: ItemLocalVideoBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(v: LocalVideo) {
            b.tvName.text = v.name; b.tvSize.text = FileUtils.formatFileSize(v.size); b.tvDuration.text = FileUtils.formatDuration(v.duration); b.tvResolution.text = if (v.width > 0) "${v.width}x${v.height}" else "—"
            b.ivThumbnail.load(v.uri) { crossfade(true); decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }; placeholder(R.drawable.ic_video_placeholder); error(R.drawable.ic_video_placeholder) }
            b.root.setOnClickListener { onPlay(v) }; b.btnMore.setOnClickListener { onMore(v, it) }
        }
    }
}

class HistoryAdapter(private val onPlay: (PlaybackHistory) -> Unit) : RecyclerView.Adapter<HistoryAdapter.VH>() {
    private var items: List<PlaybackHistory> = emptyList()
    fun submitList(list: List<PlaybackHistory>) { items = list; notifyDataSetChanged() }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(ItemPlaybackHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun onBindViewHolder(h: VH, pos: Int) { h.bind(items[pos]) }
    override fun getItemCount() = items.size
    inner class VH(private val b: ItemPlaybackHistoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(h: PlaybackHistory) {
            b.tvHistoryTitle.text = h.title
            val diff = System.currentTimeMillis() - h.timestamp
            b.tvHistoryTime.text = when { diff < 60000 -> "Just now"; diff < 3600000 -> "${diff / 60000}m ago"; diff < 86400000 -> "${diff / 3600000}h ago"; else -> "${diff / 86400000}d ago" }
            b.ivHistoryIcon.load(h.uri) { crossfade(true); decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }; placeholder(R.drawable.ic_play); error(R.drawable.ic_play) }
            b.root.setOnClickListener { onPlay(h) }
        }
    }
}
