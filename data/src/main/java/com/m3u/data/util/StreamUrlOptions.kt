package com.m3u.data.util

import java.net.URLDecoder
import java.net.URLEncoder

internal object StreamUrlOptions {
    const val USER_AGENT = "user-agent"
    const val REFERER = "referer"
    const val ORIGIN = "origin"
    const val COOKIE = "cookie"
    const val VIDEO_URL = "video-url"

    fun appendToUrl(url: String, options: Map<String, String>): String {
        if (options.isEmpty()) return url
        val encodedOptions = options.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        if (encodedOptions.isBlank()) return url

        val separator = if ('|' in url) "&" else "|"
        return "$url$separator$encodedOptions"
    }

    fun readFromUrl(url: String): Map<String, String?> {
        val index = url.indexOf('|')
        if (index == -1) return emptyMap()
        return url
            .drop(index + 1)
            .split("&")
            .filter { it.isNotBlank() }
            .associate {
                val pair = it.split("=", limit = 2)
                val key = pair.getOrNull(0).orEmpty()
                val value = pair.getOrNull(1)
                normalizeKey(decode(key)) to value?.let(::decode)
            }
    }

    fun readRequestHeadersFromUrl(url: String): Map<String, String> {
        return readFromUrl(url)
            .mapNotNull { (key, value) ->
                val header = key.toRequestHeaderNameOrNull() ?: return@mapNotNull null
                val headerValue = value?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                header to headerValue
            }
            .toMap()
    }

    fun stripFromUrl(url: String): String {
        val index = url.indexOf('|')
        return if (index == -1) url else url.take(index)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)

    private fun normalizeKey(value: String): String = when (value.lowercase()) {
        "http-user-agent", USER_AGENT -> USER_AGENT
        "http-referrer", "http-referer", "referrer", REFERER -> REFERER
        "http-origin", ORIGIN -> ORIGIN
        "http-cookie", COOKIE -> COOKIE
        VIDEO_URL -> VIDEO_URL
        else -> value
    }

    private fun String.toRequestHeaderNameOrNull(): String? = when (this) {
        USER_AGENT, VIDEO_URL -> null
        REFERER -> "Referer"
        ORIGIN -> "Origin"
        COOKIE -> "Cookie"
        else -> takeIf { it.isNotBlank() }
    }
}
