package com.videovault.ui.settings

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _theme = MutableStateFlow(prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    val themeMode: StateFlow<Int> = _theme
    private val _lang = MutableStateFlow(prefs.getString("lang", "en") ?: "en")
    val appLanguage: StateFlow<String> = _lang
    private val _quality = MutableStateFlow(prefs.getString("quality", "Best") ?: "Best")
    val downloadQuality: StateFlow<String> = _quality
    private val _autoPlay = MutableStateFlow(prefs.getBoolean("autoplay", true))
    val autoPlay: StateFlow<Boolean> = _autoPlay
    private val _toast = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toast

    fun setThemeMode(m: Int) { prefs.edit().putInt("theme", m).apply(); _theme.value = m; AppCompatDelegate.setDefaultNightMode(m) }
    fun setAppLanguage(l: String) { prefs.edit().putString("lang", l).apply(); _lang.value = l }
    fun setDownloadQuality(q: String) { prefs.edit().putString("quality", q).apply(); _quality.value = q }
    fun setAutoPlay(b: Boolean) { prefs.edit().putBoolean("autoplay", b).apply(); _autoPlay.value = b }
    fun getAppVersion(): String = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "1.0.0" } catch (_: Exception) { "1.0.0" }
    fun clearCache() { app.cacheDir.listFiles()?.forEach { it.delete() } }

    companion object {
        val THEME_OPTIONS = arrayOf("System", "Light", "Dark")
        val LANGUAGE_OPTIONS = arrayOf("English", "中文", "日本語", "Español", "Français", "Deutsch", "Русский", "العربية", "हिन्दी", "한국어")
        val LANGUAGE_VALUES = arrayOf("en", "zh", "ja", "es", "fr", "de", "ru", "ar", "hi", "ko")
        val QUALITY_OPTIONS = arrayOf("Best", "4K", "2K", "HD", "SD")
    }
}
