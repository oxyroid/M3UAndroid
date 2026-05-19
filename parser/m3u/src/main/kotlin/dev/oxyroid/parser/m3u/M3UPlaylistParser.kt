package dev.oxyroid.parser.m3u

import dev.oxyroid.parser.protocol.ParsedChannel
import dev.oxyroid.parser.protocol.Parser
import java.io.InputStream
import java.util.Locale

object M3UPlaylistParser : Parser<InputStream, ParsedChannel> {
    private const val M3U_HEADER_MARK = "#EXTM3U"
    private const val EXTINF_MARK = "#EXTINF"
    private const val EXTGRP_MARK = "#EXTGRP:"
    private const val KODI_MARK = "#KODIPROP:"
    private const val VLC_OPT_MARK = "#EXTVLCOPT:"
    private const val HLS_STREAM_INF_MARK = "#EXT-X-STREAM-INF:"
    private const val HLS_MEDIA_MARK = "#EXT-X-MEDIA:"

    private const val TVG_LOGO = "tvg-logo"
    private const val TVG_ID = "tvg-id"
    private const val TVG_NAME = "tvg-name"
    private const val GROUP_TITLE = "group-title"
    private const val CHANNEL_ID = "channel-id"
    private const val NAME = "NAME"
    private const val HLS_GROUP_ID = "GROUP-ID"
    private const val HLS_TYPE = "TYPE"
    private const val HLS_URI = "URI"
    private const val HLS_RESOLUTION = "RESOLUTION"
    private const val HLS_BANDWIDTH = "BANDWIDTH"
    private const val HTTP_USER_AGENT = "http-user-agent"
    private const val HTTP_REFERRER = "http-referrer"
    private const val INPUTSTREAM_LICENSE_TYPE = "inputstream.adaptive.license_type"
    private const val INPUTSTREAM_LICENSE_KEY = "inputstream.adaptive.license_key"

    override fun parse(input: InputStream): Sequence<ParsedChannel> = sequence {
        val pending = PendingEntry()
        input.bufferedReader().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trimPlaylistLine()
                if (line.isEmpty()) return@forEach

                if (line[0] == '#') {
                    pending.parseTag(line)?.let { yield(it) }
                    return@forEach
                }

                yield(pending.toChannel(line))
                pending.clear()
            }
        }
    }

    private data class PendingEntry(
        var duration: Double = -1.0,
        var title: String = "",
        var group: String = "",
        val attributes: MutableMap<String, String> = linkedMapOf(),
        val properties: MutableMap<String, String> = linkedMapOf(),
        val options: MutableMap<String, String> = linkedMapOf(),
    ) {
        fun parseTag(line: String): ParsedChannel? {
            when {
                line.equals(M3U_HEADER_MARK, ignoreCase = true) -> Unit
                line.startsWith("$EXTINF_MARK:", ignoreCase = true) -> parseExtInf(line.substringAfter(':'))
                line.startsWith(EXTGRP_MARK, ignoreCase = true) -> group = line.substringAfter(':').trim()
                line.startsWith(KODI_MARK, ignoreCase = true) -> properties.putOption(line.substringAfter(':'))
                line.startsWith(VLC_OPT_MARK, ignoreCase = true) -> options.putOption(line.substringAfter(':'))
                line.startsWith(HLS_STREAM_INF_MARK, ignoreCase = true) -> parseStreamInf(line.substringAfter(':'))
                line.startsWith(HLS_MEDIA_MARK, ignoreCase = true) -> {
                    val mediaAttributes = parseAttributes(line.substringAfter(':'))
                    val uri = mediaAttributes.valueOf(HLS_URI)
                    if (!uri.isNullOrBlank()) {
                        attributes.putAll(mediaAttributes)
                        title = mediaAttributes.valueOf(NAME).orEmpty()
                        return toChannel(uri).also { clear() }
                    }
                }
            }
            return null
        }

        fun toChannel(url: String): ParsedChannel {
            val channelName = attributes.valueOf(TVG_NAME, NAME).orEmpty()
            val channelTitle = title.ifBlank {
                channelName.ifBlank { url.substringAfterLast('/').substringBefore('?') }
            }
            val channelGroup = attributes.valueOf(GROUP_TITLE, "group", HLS_GROUP_ID, HLS_TYPE).orEmpty()
            return ParsedChannel(
                id = attributes.valueOf(TVG_ID, CHANNEL_ID).orEmpty(),
                name = channelName,
                cover = attributes.valueOf(TVG_LOGO, "logo").orEmpty(),
                group = channelGroup.ifBlank { group },
                title = channelTitle,
                url = url,
                duration = duration,
                licenseType = properties.valueOf(INPUTSTREAM_LICENSE_TYPE),
                licenseKey = properties.valueOf(INPUTSTREAM_LICENSE_KEY),
                userAgent = options.valueOf(HTTP_USER_AGENT),
                referrer = options.valueOf(HTTP_REFERRER),
                options = options.toMap(),
            )
        }

        fun clear() {
            duration = -1.0
            title = ""
            group = ""
            attributes.clear()
            properties.clear()
            options.clear()
        }

        private fun parseExtInf(value: String) {
            val commaIndex = value.indexOfUnquoted(',')
            val prefix = if (commaIndex >= 0) value.substring(0, commaIndex) else value
            title = if (commaIndex >= 0) value.substring(commaIndex + 1).trim() else ""
            val trimmedPrefix = prefix.trim()
            val durationEnd = trimmedPrefix.indexOfFirst { it.isWhitespace() }
            val durationText = if (durationEnd >= 0) {
                trimmedPrefix.substring(0, durationEnd)
            } else {
                trimmedPrefix
            }
            duration = durationText.toDoubleOrNull() ?: -1.0
            if (durationEnd >= 0) {
                attributes.putAll(parseAttributes(trimmedPrefix.substring(durationEnd + 1)))
            }
        }

        private fun parseStreamInf(value: String) {
            attributes.putAll(parseAttributes(value))
            title = attributes.valueOf(NAME, HLS_RESOLUTION, HLS_BANDWIDTH).orEmpty()
        }
    }

    private fun String.trimPlaylistLine(): String {
        val start = if (isNotEmpty() && this[0] == '\uFEFF') 1 else 0
        return substring(start).trim()
    }

    private fun MutableMap<String, String>.putOption(value: String) {
        val separator = value.indexOf('=')
        if (separator <= 0) return
        val key = value.substring(0, separator).trim()
        val optionValue = value.substring(separator + 1).trim().trimMatchingQuotes()
        if (key.isNotEmpty()) put(key, optionValue)
    }

    private fun parseAttributes(value: String): Map<String, String> {
        val attributes = linkedMapOf<String, String>()
        var index = 0
        while (index < value.length) {
            index = value.skipSeparators(index)
            if (index >= value.length) break

            val keyStart = index
            while (index < value.length && value[index] != '=') index++
            if (index >= value.length) break

            val key = value.substring(keyStart, index).trim().trimEnd(',')
            index++
            if (key.isEmpty()) continue

            val parsedValue = if (index < value.length && value[index] == '"') {
                value.readQuoted(index)
            } else {
                value.readUnquoted(index)
            }
            if (parsedValue.text.isNotEmpty()) attributes[key] = parsedValue.text
            index = parsedValue.nextIndex
        }
        return attributes
    }

    private data class ParsedValue(
        val text: String,
        val nextIndex: Int,
    )

    private fun String.readQuoted(startIndex: Int): ParsedValue {
        val builder = StringBuilder()
        var index = startIndex + 1
        while (index < length) {
            val char = this[index]
            when {
                char == '\\' && index + 1 < length -> {
                    builder.append(this[index + 1])
                    index += 2
                }
                char == '"' -> return ParsedValue(builder.toString(), index + 1)
                else -> {
                    builder.append(char)
                    index++
                }
            }
        }
        return ParsedValue(builder.toString(), index)
    }

    private fun String.readUnquoted(startIndex: Int): ParsedValue {
        var index = startIndex
        while (index < length && this[index] != ',' && !this[index].isWhitespace()) index++
        return ParsedValue(substring(startIndex, index).trim().trimMatchingQuotes(), index)
    }

    private fun String.skipSeparators(startIndex: Int): Int {
        var index = startIndex
        while (index < length && (this[index] == ',' || this[index].isWhitespace())) index++
        return index
    }

    private fun String.indexOfUnquoted(target: Char): Int {
        var quoted = false
        var escaped = false
        for (index in indices) {
            val char = this[index]
            when {
                escaped -> escaped = false
                char == '\\' && quoted -> escaped = true
                char == '"' -> quoted = !quoted
                char == target && !quoted -> return index
            }
        }
        return -1
    }

    private fun String.trimMatchingQuotes(): String {
        return if (length >= 2 && first() == '"' && last() == '"') substring(1, lastIndex) else this
    }

    private fun Map<String, String>.valueOf(vararg keys: String): String? {
        for (key in keys) {
            this[key]?.let { return it }
            this[key.lowercase(Locale.ROOT)]?.let { return it }
            this[key.uppercase(Locale.ROOT)]?.let { return it }
        }
        return null
    }
}
