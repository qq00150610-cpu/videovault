package com.videovault.data.remote.repository

import com.videovault.data.model.VideoInfo
import com.videovault.data.remote.api.CobaltApiService
import com.videovault.data.remote.api.CobaltMedia
import com.videovault.data.remote.api.ParseResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class VideoRepository(private val api: CobaltApiService = CobaltApiService.getInstance()) {

    /**
     * Parse any supported URL. Returns video info for the existing UI pipeline,
     * or a picker list when the source has multiple items (e.g. Instagram carousel).
     */
    suspend fun parseVideoFromUrl(
        url: String,
        quality: String = "1080",
        mode: String = "auto"
    ): Result<VideoInfo> {
        val result = api.parseUrl(url, videoQuality = quality, downloadMode = mode)
        return result.fold(
            onSuccess = { parseResult ->
                when (parseResult) {
                    is ParseResult.Single -> {
                        val info = api.buildVideoInfo(url, parseResult.media)
                        Result.success(info)
                    }
                    is ParseResult.Picker -> {
                        // Use first video item from picker
                        val video = parseResult.items.firstOrNull { it.type == "video" || it.type == "gif" }
                            ?: parseResult.items.firstOrNull()
                        if (video != null) {
                            Result.success(api.buildVideoInfo(url, video))
                        } else {
                            Result.failure(Exception("No media found"))
                        }
                    }
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

    /**
     * Parse URL and return raw cobalt media for direct download.
     */
    suspend fun parseForDownload(url: String, quality: String = "1080"): Result<ParseResult> {
        return api.parseUrl(url, videoQuality = quality)
    }

    fun parseVideoFromUrlFlow(url: String, quality: String = "1080"): Flow<VideoParseState> = flow {
        emit(VideoParseState.Loading)
        parseVideoFromUrl(url, quality).fold(
            onSuccess = { emit(VideoParseState.Success(it)) },
            onFailure = { emit(VideoParseState.Error(it.message ?: "Failed")) }
        )
    }

    fun isValidUrl(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }

    fun isTwitterUrl(url: String): Boolean {
        return url.contains("twitter.com") || url.contains("x.com")
    }
}

sealed class VideoParseState {
    object Idle : VideoParseState()
    object Loading : VideoParseState()
    data class Success(val videoInfo: VideoInfo) : VideoParseState()
    data class Error(val message: String) : VideoParseState()
}
