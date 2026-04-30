package com.videovault.data.remote.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.videovault.data.model.VideoInfo
import com.videovault.data.model.VideoVariant
import com.videovault.data.model.GifVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Cobalt API service — supports Twitter/X, YouTube, TikTok, Instagram, Reddit,
 * Facebook, Vimeo, SoundCloud, Twitch, Pinterest, Vine, Tumblr, and more.
 *
 * API docs: https://github.com/imputnet/cobalt/blob/main/docs/api.md
 */
class CobaltApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val gson = Gson()

    // Default public cobalt instances (users can configure their own)
    private var apiEndpoint = DEFAULT_API_ENDPOINT

    fun setEndpoint(endpoint: String) {
        apiEndpoint = endpoint.trimEnd('/')
    }

    /**
     * Parse any supported URL via cobalt API.
     * Returns a ParseResult which can be a single video, a picker (multiple items), or an error.
     */
    suspend fun parseUrl(
        url: String,
        videoQuality: String = "1080",
        downloadMode: String = "auto",
        audioFormat: String = "mp3",
        audioBitrate: String = "128"
    ): Result<ParseResult> = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("url", url)
                put("videoQuality", videoQuality)
                put("downloadMode", downloadMode)
                put("audioFormat", audioFormat)
                put("audioBitrate", audioBitrate)
                put("filenameStyle", "pretty")
                put("convertGif", true)
            }

            val request = Request.Builder()
                .url("$apiEndpoint/")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))

            if (!response.isSuccessful) {
                val errorObj = try {
                    JsonParser.parseString(responseBody).asJsonObject
                } catch (_: Exception) { null }
                val errorCode = errorObj?.getAsJsonObject("error")?.get("code")?.asString
                return@withContext Result.failure(Exception(errorCode ?: "HTTP ${response.code}"))
            }

            val json = JsonParser.parseString(responseBody).asJsonObject
            val status = json.get("status")?.asString ?: "error"

            when (status) {
                "tunnel", "redirect" -> {
                    val downloadUrl = json.get("url")?.asString ?: return@withContext Result.failure(Exception("No URL in response"))
                    val filename = json.get("filename")?.asString ?: "video.mp4"
                    Result.success(ParseResult.Single(CobaltMedia(url = downloadUrl, filename = filename, type = "video")))
                }
                "picker" -> {
                    val pickerArray = json.getAsJsonArray("picker") ?: return@withContext Result.failure(Exception("Empty picker"))
                    val items = pickerArray.mapNotNull { item ->
                        val obj = item.asJsonObject
                        val itemUrl = obj.get("url")?.asString ?: return@mapNotNull null
                        val type = obj.get("type")?.asString ?: "video"
                        val thumb = obj.get("thumb")?.asString
                        CobaltMedia(url = itemUrl, filename = "${type}_${System.currentTimeMillis()}", type = type, thumbnail = thumb)
                    }
                    val audio = json.get("audio")?.asString
                    val audioFilename = json.get("audioFilename")?.asString
                    Result.success(ParseResult.Picker(items, audio, audioFilename))
                }
                "local-processing" -> {
                    // For local processing, we get tunnel URLs that need merging
                    val tunnels = json.getAsJsonArray("tunnel")?.map { it.asString } ?: emptyList()
                    val output = json.getAsJsonObject("output")
                    val filename = output?.get("filename")?.asString ?: "video.mp4"
                    val mimeType = output?.get("type")?.asString ?: "video/mp4"
                    if (tunnels.isNotEmpty()) {
                        // Use first tunnel URL as direct download
                        Result.success(ParseResult.Single(CobaltMedia(url = tunnels.first(), filename = filename, type = mimeType)))
                    } else {
                        Result.failure(Exception("No tunnel URLs"))
                    }
                }
                "error" -> {
                    val err = json.getAsJsonObject("error")
                    val code = err?.get("code")?.asString ?: "unknown_error"
                    Result.failure(Exception(code))
                }
                else -> Result.failure(Exception("Unknown status: $status"))
            }
        } catch (e: Exception) {
            // Fallback: try vxtwitter for Twitter URLs
            if (url.contains("twitter.com") || url.contains("x.com")) {
                return@withContext fallbackParseTwitter(url)
            }
            Result.failure(e)
        }
    }

    /**
     * Fallback parser for Twitter using vxtwitter API
     */
    private suspend fun fallbackParseTwitter(url: String): Result<ParseResult> {
        try {
            val tweetId = extractTweetId(url) ?: return Result.failure(Exception("Invalid Twitter URL"))
            val request = Request.Builder()
                .url("https://api.vxtwitter.com/$tweetId")
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()?.let { json ->
                    val parser = JsonParser.parseString(json).asJsonObject
                    val variants = mutableListOf<VideoVariant>()
                    parser.getAsJsonArray("media")?.forEach { m ->
                        val mo = m.asJsonObject
                        if (mo.get("type")?.asString == "video") {
                            mo.getAsJsonObject("video_info")?.getAsJsonArray("variants")?.forEach { v ->
                                val vo = v.asJsonObject
                                val u = vo.get("url")?.asString ?: return@forEach
                                val br = vo.get("bitrate")?.asInt ?: 0
                                if (vo.get("content_type")?.asString == "video/mp4") {
                                    variants.add(VideoVariant(u, br, "video/mp4"))
                                }
                            }
                        }
                    }
                    if (variants.isNotEmpty()) {
                        val best = variants.maxByOrNull { it.bitrate } ?: variants.first()
                        return Result.success(ParseResult.Single(CobaltMedia(url = best.url, filename = "twitter_$tweetId.mp4", type = "video")))
                    }
                }
            }
        } catch (_: Exception) {}
        return Result.failure(Exception("Fallback failed"))
    }

    private fun extractTweetId(url: String): String? {
        val regex = Regex("(?:twitter|x)\\.com/\\w+/status/(\\d+)")
        return regex.find(url)?.groupValues?.get(1)
    }

    /**
     * Build a VideoInfo from a cobalt media result for compatibility with existing UI
     */
    fun buildVideoInfo(url: String, media: CobaltMedia): VideoInfo {
        val tweetId = extractTweetId(url) ?: media.hashCode().toString()
        return VideoInfo(
            tweetId = tweetId,
            tweetUrl = url,
            authorName = extractDomain(url),
            authorUsername = extractDomain(url),
            tweetText = media.filename,
            thumbnailUrl = media.thumbnail,
            videoVariants = listOf(VideoVariant(media.url, 1000000, "video/mp4")),
            gifVariants = emptyList()
        )
    }

    private fun extractDomain(url: String): String {
        return try {
            val host = java.net.URI(url).host ?: "Unknown"
            host.removePrefix("www.").replaceFirstChar { it.uppercase() }
        } catch (_: Exception) { "Unknown" }
    }

    private fun buildJsonObject(block: com.google.gson.JsonObject.() -> Unit): com.google.gson.JsonObject {
        return com.google.gson.JsonObject().apply(block)
    }

    private fun com.google.gson.JsonObject.put(key: String, value: String) = addProperty(key, value)
    private fun com.google.gson.JsonObject.put(key: String, value: Boolean) = addProperty(key, value)

    companion object {
        const val DEFAULT_API_ENDPOINT = "https://api.cobalt.tools"

        @Volatile
        private var instance: CobaltApiService? = null

        fun getInstance(): CobaltApiService {
            return instance ?: synchronized(this) {
                instance ?: CobaltApiService().also { instance = it }
            }
        }
    }
}

/** Result types from cobalt API */
sealed class ParseResult {
    data class Single(val media: CobaltMedia) : ParseResult()
    data class Picker(val items: List<CobaltMedia>, val audioUrl: String? = null, val audioFilename: String? = null) : ParseResult()
}

data class CobaltMedia(
    val url: String,
    val filename: String,
    val type: String, // "video", "photo", "gif", "audio", or MIME type
    val thumbnail: String? = null
)
