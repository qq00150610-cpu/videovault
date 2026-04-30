package com.videovault.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.videovault.R
import com.videovault.databinding.FragmentSettingsBinding
import com.videovault.ui.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val vm: SettingsViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false); return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.itemTheme.setOnClickListener { showThemeDialog() }
        binding.itemLanguage.setOnClickListener { showLangDialog() }
        binding.itemQuality.setOnClickListener { showQualityDialog() }
        binding.switchAutoPlay.setOnCheckedChangeListener { _, checked -> vm.setAutoPlay(checked) }
        binding.itemClearCache.setOnClickListener { MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.clear_cache_title).setMessage(R.string.clear_cache_message).setPositiveButton(R.string.clear) { _, _ -> vm.clearCache(); Snackbar.make(binding.root, R.string.cache_cleared, Snackbar.LENGTH_SHORT).show() }.setNegativeButton(R.string.cancel, null).show() }
        binding.tvVersion.text = getString(R.string.version_format, vm.getAppVersion())
        binding.itemAbout.setOnClickListener { MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.app_name).setMessage(getString(R.string.about_content, vm.getAppVersion())).setPositiveButton(R.string.ok, null).show() }
        observeState()
    }

    private fun showThemeDialog() {
        val idx = when (vm.themeMode.value) { AppCompatDelegate.MODE_NIGHT_NO -> 1; AppCompatDelegate.MODE_NIGHT_YES -> 2; else -> 0 }
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.theme).setSingleChoiceItems(SettingsViewModel.THEME_OPTIONS, idx) { d, w ->
            vm.setThemeMode(when (w) { 1 -> AppCompatDelegate.MODE_NIGHT_NO; 2 -> AppCompatDelegate.MODE_NIGHT_YES; else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }); d.dismiss()
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showLangDialog() {
        val idx = SettingsViewModel.LANGUAGE_VALUES.indexOf(vm.appLanguage.value).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.select_language).setSingleChoiceItems(SettingsViewModel.LANGUAGE_OPTIONS, idx) { d, w ->
            vm.setAppLanguage(SettingsViewModel.LANGUAGE_VALUES[w]); d.dismiss()
            activity?.let { startActivity(Intent(it, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)); it.finish() }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showQualityDialog() {
        val idx = SettingsViewModel.QUALITY_OPTIONS.indexOf(vm.downloadQuality.value).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.default_quality).setSingleChoiceItems(SettingsViewModel.QUALITY_OPTIONS, idx) { d, w -> vm.setDownloadQuality(SettingsViewModel.QUALITY_OPTIONS[w]); d.dismiss() }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch { viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { vm.themeMode.collectLatest { binding.tvThemeValue.text = when (it) { AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.theme_light); AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.theme_dark); else -> getString(R.string.theme_system) } }}
            launch { vm.appLanguage.collectLatest { binding.tvLanguageValue.text = SettingsViewModel.LANGUAGE_OPTIONS[SettingsViewModel.LANGUAGE_VALUES.indexOf(it).coerceAtLeast(0)] }}
            launch { vm.downloadQuality.collectLatest { binding.tvQualityValue.text = it }}
            launch { vm.autoPlay.collectLatest { binding.switchAutoPlay.isChecked = it }}
        }}
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
