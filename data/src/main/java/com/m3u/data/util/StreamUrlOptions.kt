package com.m3u.data.util

import java.net.URLDecoder
import java.net.URLEncoder

object StreamUrlOptions {
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

        val separator = if (url.findOptionDelimiter() != null) "&" else "|"
        return "$url$separator$encodedOptions"
    }

    fun readFromUrl(url: String): Map<String, String?> {
        val delimiter = url.findOptionDelimiter() ?: return emptyMap()
        val optionsText = url.drop(delimiter.index + delimiter.length)
        return parseOptionParameters(optionsText)
    }

    private fun parseOptionParameters(text: String): Map<String, String?> {
        return text
            .split("&")
            .filter { it.isNotBlank() }
            .associate {
                val pair = it.splitOptionPair()
                val key = pair.first
                val value = pair.second
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
        val delimiter = url.findOptionDelimiter()
        return if (delimiter == null) url else url.take(delimiter.index)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun decode(value: String): String = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)

    private fun String.splitOptionPair(): Pair<String, String?> {
        val literalPair = split("=", limit = 2)
        if (literalPair.size > 1) {
            return literalPair[0] to literalPair[1]
        }
        val decodedPair = decode(this).split("=", limit = 2)
        return decodedPair[0] to decodedPair.getOrNull(1)
    }

    private fun String.findOptionDelimiter(): OptionDelimiter? {
        val literalDelimiter = indexOf('|')
            .takeIf { it != -1 }
            ?.let { OptionDelimiter(index = it, length = 1) }

        val encodedPipeRegex = "%7c".toRegex(RegexOption.IGNORE_CASE)
        val encodedDelimiter = encodedPipeRegex
            .find(this)
            ?.takeIf { match ->
                val key = substring(match.range.last + 1)
                    .substringBefore("&")
                    .splitOptionPair()
                    .first
                    .let(::decode)
                normalizeKey(key).isKnownOptionKey() || key.lowercase().startsWith("http-")
            }
            ?.let { OptionDelimiter(index = it.range.first, length = it.value.length) }

        return listOfNotNull(literalDelimiter, encodedDelimiter)
            .minByOrNull { it.index }
    }

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

    private fun String.isKnownOptionKey(): Boolean = when (this) {
        USER_AGENT, REFERER, ORIGIN, COOKIE, VIDEO_URL -> true
        else -> false
    }

    private data class OptionDelimiter(
        val index: Int,
        val length: Int
    )
}
