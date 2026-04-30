package com.videovault.util

import android.content.Context
import com.videovault.data.model.PlaybackHistory
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PlaybackHistoryManager private constructor(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("playback_history", Context.MODE_PRIVATE)

    fun addHistory(title: String, uri: String, duration: Long = 0) {
        val list = getHistory().toMutableList()
        list.removeAll { it.uri == uri }
        list.add(0, PlaybackHistory(UUID.randomUUID().toString(), title.ifEmpty { "Unknown" }, uri, System.currentTimeMillis(), duration))
        save(list.take(100))
    }

    fun getHistory(): List<PlaybackHistory> {
        val s = prefs.getString("history", null) ?: return emptyList()
        return try {
            val a = JSONArray(s)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                PlaybackHistory(o.getString("id"), o.getString("title"), o.getString("uri"), o.optLong("ts", System.currentTimeMillis()), o.optLong("dur", 0))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun clearHistory() = prefs.edit().remove("history").apply()

    private fun save(list: List<PlaybackHistory>) {
        val a = JSONArray()
        list.forEach { h ->
            a.put(JSONObject().apply { put("id", h.id); put("title", h.title); put("uri", h.uri); put("ts", h.timestamp); put("dur", h.duration) })
        }
        prefs.edit().putString("history", a.toString()).apply()
    }

    companion object {
        @Volatile private var inst: PlaybackHistoryManager? = null
        fun getInstance(ctx: Context) = inst ?: synchronized(this) { inst ?: PlaybackHistoryManager(ctx.applicationContext).also { inst = it } }
    }
}
