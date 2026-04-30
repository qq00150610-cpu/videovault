package com.videovault.util

object UrlUtils {
    private val twitterPatterns = listOf(
        Regex("twitter\\.com/\\w+/status/\\d+"),
        Regex("x\\.com/\\w+/status/\\d+"),
        Regex("https://t\\.co/\\w+")
    )

    fun isValidTwitterUrl(url: String) = twitterPatterns.any { it.containsMatchIn(url.trim()) }

    fun normalizeTwitterUrl(url: String): String {
        var n = url.trim()
        n = n.replace("mobile.twitter.com", "x.com")
        n = n.replace("twitter.com", "x.com")
        return n
    }

    fun extractTweetId(url: String): String? {
        for (p in listOf(Regex("x\\.com/\\w+/status/(\\d+)"), Regex("twitter\\.com/\\w+/status/(\\d+)"), Regex("status/(\\d+)"))) {
            p.find(url)?.let { return it.groupValues[1] }
        }
        return null
    }

    fun extractUsername(url: String): String? {
        for (p in listOf(Regex("x\\.com/(\\w+)/status"), Regex("twitter\\.com/(\\w+)/status"))) {
            p.find(url)?.let { return it.groupValues[1] }
        }
        return null
    }
}
