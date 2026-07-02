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
                decode(key) to value?.let(::decode)
            }
    }

    fun stripFromUrl(url: String): String {
        val index = url.indexOf('|')
        return if (index == -1) url else url.take(index)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)
}
