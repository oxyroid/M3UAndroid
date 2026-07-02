package com.m3u.data.parser.m3u

import com.m3u.data.util.StreamUrlOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.io.InputStream
import javax.inject.Inject

internal class M3UParserImpl @Inject constructor() : M3UParser {
    private val timber = Timber.tag("M3UParserImpl")
    companion object {
        private const val M3U_HEADER_MARK = "#EXTM3U"
        private const val M3U_INFO_MARK = "#EXTINF:"
        private const val KODI_MARK = "#KODIPROP:"
        private const val VLC_OPT_MARK = "#EXTVLCOPT:"
        private const val EXT_HTTP_MARK = "#EXTHTTP:"
        private const val TXT_GROUP_MARK = "#genre#"

        private val infoRegex = """(-?\d+)(.*),(.+)""".toRegex()
        private val propertyRegex = """([^=]+)=(.*)""".toRegex()
        private val metadataRegex = """([\w-_.]+)=\s*(?:"([^"]*)"|(\S+))""".toRegex()

        private const val M3U_TVG_LOGO_MARK = "tvg-logo"
        const val M3U_TVG_ID_MARK = "tvg-id"
        const val M3U_TVG_NAME_MARK = "tvg-name"
        const val M3U_GROUP_TITLE_MARK = "group-title"

        const val KODI_LICENSE_TYPE = "inputstream.adaptive.license_type"
        const val KODI_LICENSE_KEY = "inputstream.adaptive.license_key"

        private const val VLC_USER_AGENT = "http-user-agent"
        private const val VLC_REFERER = "http-referrer"
        private const val VLC_REFERER_ALT = "http-referer"
        private const val VLC_ORIGIN = "http-origin"
        private const val EXT_HTTP_COOKIE = "cookie"

        private val supportedTxtUrlSchemes = listOf(
            "http://",
            "https://",
            "rtmp://",
            "rtsp://",
            "rtp://",
            "udp://",
            "file:///"
        )
    }

    override fun parse(input: InputStream): Flow<M3UData> = flow {
        val lines = input
            .bufferedReader()
            .lineSequence()
            .filter { it.isNotEmpty() }
            .map { it.trimEnd() }
            .dropWhile { it.startsWith(M3U_HEADER_MARK) }
            .iterator()

        var currentLine: String
        var txtGroup = ""
        var infoMatch: MatchResult? = null
        val kodiMatches = mutableListOf<MatchResult>()
        val httpOptions = mutableMapOf<String, String>()

        while (lines.hasNext()) {
            currentLine = lines.next()
            while (currentLine.startsWith("#")) {
                timber.d("Parsing protocol line: $currentLine")
                if (currentLine.startsWith(M3U_INFO_MARK)) {
                    infoMatch = infoRegex
                        .matchEntire(currentLine.drop(M3U_INFO_MARK.length).trim())
                }
                if (currentLine.startsWith(KODI_MARK)) {
                    propertyRegex
                        .matchEntire(currentLine.drop(KODI_MARK.length).trim())
                        ?.also { kodiMatches += it }
                }
                if (currentLine.startsWith(VLC_OPT_MARK)) {
                    propertyRegex
                        .matchEntire(currentLine.drop(VLC_OPT_MARK.length).trim())
                        ?.let { match ->
                            val key = match.groups[1]!!.value.trim()
                            val value = match.groups[2]?.value.orEmpty().trim()
                            httpOptions += key.toHttpOptionKey() to value
                        }
                }
                if (currentLine.startsWith(EXT_HTTP_MARK)) {
                    httpOptions += currentLine
                        .drop(EXT_HTTP_MARK.length)
                        .trim()
                        .parseExtHttpOptions()
                }
                if (lines.hasNext()) {
                    currentLine = lines.next()
                }
            }
            if (infoMatch == null && !currentLine.startsWith("#")) {
                with(currentLine.trim()) {
                    parseTxtGroupTitleOrNull()
                        ?.let { title -> txtGroup = title }
                        ?: parseTxtData(txtGroup)
                            ?.let { data -> emit(data) }
                }
                continue
            }

            val title = infoMatch?.groups?.get(3)?.value.orEmpty().trim()
            val duration = infoMatch?.groups?.get(1)?.value?.toDouble() ?: -1.0
            val metadata = buildMap {
                val text = infoMatch?.groups?.get(2)?.value.orEmpty().trim()
                val matches = metadataRegex.findAll(text)
                for (match in matches) {
                    val key = match.groups[1]!!.value
                    val value = match.groups[2]?.value?.ifBlank { null }
                        ?: match.groups[3]?.value?.ifBlank { null }
                        ?: continue
                    put(key.trim(), value.trim())
                }
            }
            val kodiMetadata = buildMap {
                for (match in kodiMatches) {
                    val key = match.groups[1]!!.value
                    val value = match.groups[2]?.value?.ifBlank { null } ?: continue
                    put(key.trim(), value.trim())
                }
            }
            val urls = currentLine.splitSeparateStreamUrls()
            val entry = M3UData(
                id = metadata[M3U_TVG_ID_MARK].orEmpty(),
                name = metadata[M3U_TVG_NAME_MARK].orEmpty(),
                cover = metadata[M3U_TVG_LOGO_MARK].orEmpty(),
                group = metadata[M3U_GROUP_TITLE_MARK].orEmpty(),
                title = title,
                url = urls.audioUrl,
                videoUrl = urls.videoUrl,
                duration = duration,
                licenseType = kodiMetadata[KODI_LICENSE_TYPE],
                licenseKey = kodiMetadata[KODI_LICENSE_KEY],
                httpOptions = httpOptions.filterValues { it.isNotBlank() },
            )

            infoMatch = null
            kodiMatches.clear()
            httpOptions.clear()

            emit(entry)
        }
    }
        .flowOn(Dispatchers.Default)

    private fun String.toHttpOptionKey(): String = when (lowercase()) {
        VLC_USER_AGENT -> StreamUrlOptions.USER_AGENT
        VLC_REFERER, VLC_REFERER_ALT -> StreamUrlOptions.REFERER
        VLC_ORIGIN -> StreamUrlOptions.ORIGIN
        else -> removePrefix("http-")
    }

    private fun String.parseTxtData(currentGroup: String): M3UData? {
        val commaIndex = indexOf(',')
        if (commaIndex <= 0) return null

        val title = substring(0, commaIndex).trim()
        if (title.isBlank()) return null

        val text = substring(commaIndex + 1).trim()
        if (text.equals(TXT_GROUP_MARK, ignoreCase = true)) return null

        val url = text.firstSupportedTxtUrlOrNull() ?: return null
        val urls = url.splitSeparateStreamUrls()
        return M3UData(
            group = currentGroup,
            title = title,
            url = urls.audioUrl,
            videoUrl = urls.videoUrl
        )
    }

    private fun String.parseTxtGroupTitleOrNull(): String? {
        val commaIndex = indexOf(',')
        if (commaIndex <= 0) return null

        val title = substring(0, commaIndex).trim()
        if (title.isBlank()) return null

        return title.takeIf {
            substring(commaIndex + 1)
                .trim()
                .equals(TXT_GROUP_MARK, ignoreCase = true)
        }
    }

    private fun String.firstSupportedTxtUrlOrNull(): String? {
        val start = supportedTxtUrlSchemes
            .map { scheme -> indexOf(scheme, ignoreCase = true) }
            .filter { it >= 0 }
            .minOrNull() ?: return null

        return substring(start)
            .substringBefore("#")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun String.splitSeparateStreamUrls(): SeparateStreamUrls {
        val text = trim()
        for (index in text.indices) {
            if (text[index] != ';') continue
            val candidate = text.drop(index + 1).trim()
            if (candidate.startsWithSupportedTxtUrlScheme()) {
                return SeparateStreamUrls(
                    audioUrl = text.take(index).trim(),
                    videoUrl = candidate
                )
            }
        }
        return SeparateStreamUrls(audioUrl = text)
    }

    private fun String.startsWithSupportedTxtUrlScheme(): Boolean {
        return supportedTxtUrlSchemes.any { scheme -> startsWith(scheme, ignoreCase = true) }
    }

    private fun String.parseExtHttpOptions(): Map<String, String> = runCatching {
        Json.parseToJsonElement(this)
            .jsonObject
            .toHttpOptions()
    }.getOrDefault(emptyMap())

    private fun JsonObject.toHttpOptions(): Map<String, String> = buildMap {
        for ((key, element) in this@toHttpOptions) {
            val value = runCatching {
                element.jsonPrimitive.content
            }.getOrNull() ?: continue
            val optionKey = when (key.lowercase()) {
                EXT_HTTP_COOKIE -> StreamUrlOptions.COOKIE
                VLC_USER_AGENT, StreamUrlOptions.USER_AGENT -> StreamUrlOptions.USER_AGENT
                VLC_REFERER, VLC_REFERER_ALT, StreamUrlOptions.REFERER -> StreamUrlOptions.REFERER
                VLC_ORIGIN, StreamUrlOptions.ORIGIN -> StreamUrlOptions.ORIGIN
                else -> key
            }
            put(optionKey, value)
        }
    }

    private data class SeparateStreamUrls(
        val audioUrl: String,
        val videoUrl: String? = null
    )
}
