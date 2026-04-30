package com.videovault.ui.online

import android.content.ClipboardManager
import android.content.Context
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.videovault.R
import com.videovault.databinding.FragmentOnlinePlayerBinding
import com.videovault.ui.player.PlayerActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class OnlinePlayerFragment : Fragment() {
    private var _binding: FragmentOnlinePlayerBinding? = null
    private val binding get() = _binding!!
    private val vm: OnlinePlayerViewModel by viewModels()
    private lateinit var adapter: RecentUrlAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnlinePlayerBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPaste.setOnClickListener {
            val cb = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cb.primaryClip?.getItemAt(0)?.text?.toString()
            if (!t.isNullOrEmpty()) binding.etUrl.setText(t) else Snackbar.make(binding.root, R.string.clipboard_empty, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnPlay.setOnClickListener {
            val u = binding.etUrl.text.toString().trim()
            if (u.isNotEmpty()) playVideo(u) else Snackbar.make(binding.root, R.string.please_enter_url, Snackbar.LENGTH_SHORT).show()
        }
        binding.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.clear_history_title).setMessage(R.string.clear_history_message)
                .setPositiveButton(R.string.clear) { _, _ -> vm.clearHistory() }.setNegativeButton(R.string.cancel, null).show()
        }
        adapter = RecentUrlAdapter({ binding.etUrl.setText(it.url); playVideo(it.url) }, { vm.removeFromHistory(it.url) })
        binding.rvRecentUrls.layoutManager = LinearLayoutManager(context); binding.rvRecentUrls.adapter = adapter
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.recentUrls.collectLatest { adapter.submitList(it); binding.layoutRecentHistory.isVisible = it.isNotEmpty() }
        }}
    }

    private fun playVideo(url: String) {
        if (!isValidUrl(url)) { Snackbar.make(binding.root, R.string.error_invalid_url_general, Snackbar.LENGTH_SHORT).show(); return }
        vm.addToHistory(url)
        startActivity(Intent(requireContext(), PlayerActivity::class.java).apply { putExtra(PlayerActivity.EXTRA_VIDEO_URL, url); putExtra(PlayerActivity.EXTRA_IS_STREAMING, true) })
    }

    private fun isValidUrl(url: String) = try { val u = android.net.Uri.parse(url); u.scheme != null && (u.scheme == "http" || u.scheme == "https") } catch (_: Exception) { false }
    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
