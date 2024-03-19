package com.m3u.data.parser.m3u

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.LinkedList
import javax.inject.Inject

internal class M3UParserImpl @Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher
) : M3UParser {
    companion object {
        private const val M3U_HEADER_MARK = "#EXTM3U"
        private const val M3U_INFO_MARK = "#EXTINF:"
        private const val KODI_MARK = "#KODIPROP:"

        private val infoRegex = """$M3U_INFO_MARK(-?\d+)(.*),(.+)""".toRegex()
        private val kodiPropRegex = """$KODI_MARK(.+)=(.+)""".toRegex()
        private val metadataRegex = """([\w-_.]+)=\s*(?:"([^"]*)"|(\S+))""".toRegex()

        private const val M3U_TVG_LOGO_MARK = "tvg-logo"
        const val M3U_TVG_ID_MARK = "tvg-id"
        const val M3U_TVG_NAME_MARK = "tvg-name"
        const val M3U_GROUP_TITLE_MARK = "group-title"

        const val KODI_LICENSE_TYPE = "inputstream.adaptive.license_type"
        const val KODI_LICENSE_KEY = "inputstream.adaptive.license_key"
    }

    override suspend fun execute(
        input: InputStream,
        callback: (count: Int, total: Int) -> Unit
    ): List<M3UData> = withContext(ioDispatcher) {
        var currentCount = 0
        callback(currentCount, -1)
        val lines = input
            .bufferedReader()
            .lineSequence()
            .filter { it.isNotEmpty() }
            .map { it.trimEnd() }
            .dropWhile { it == M3U_HEADER_MARK }
            .iterator()

        if (!lines.hasNext()) return@withContext emptyList<M3UData>()

        val entries = LinkedList<M3UData>()

        var currentLine: String
        var infoMatch: MatchResult? = null
        val kodiMatches = mutableListOf<MatchResult>()

        while (lines.hasNext()) {
            currentLine = lines.next()
            while (currentLine.startsWith("#")) {
                if (currentLine.startsWith(M3U_INFO_MARK)) {
                    infoMatch = infoRegex.matchEntire(currentLine)
                }
                if (currentLine.startsWith(KODI_MARK)) {
                    kodiPropRegex.matchEntire(currentLine)?.also { kodiMatches += it }
                }
                if (lines.hasNext()) {
                    currentLine = lines.next()
                } else {
                    return@withContext entries
                }
            }
            if (infoMatch == null && !currentLine.startsWith("#")) continue

            val title = infoMatch?.groups?.get(3)?.value.orEmpty()
            val duration = infoMatch?.groups?.get(1)?.value?.toDouble() ?: -1.0
            val metadata = buildMap {
                val text = infoMatch?.groups?.get(2)?.value.orEmpty().trim()
                val matches = metadataRegex.findAll(text)
                for (match in matches) {
                    val key = match.groups[1]!!.value
                    val value = match.groups[2]?.value?.ifBlank { null } ?: continue
                    put(key, value)
                }
            }
            val kodiMetadata = buildMap {
                for (match in kodiMatches) {
                    val key = match.groups[1]!!.value
                    val value = match.groups[2]?.value?.ifBlank { null } ?: continue
                    put(key, value)
                }
            }
            val entry = M3UData(
                id = metadata[M3U_TVG_ID_MARK].orEmpty(),
                name = metadata[M3U_TVG_NAME_MARK].orEmpty(),
                cover = metadata[M3U_TVG_LOGO_MARK].orEmpty(),
                group = metadata[M3U_GROUP_TITLE_MARK].orEmpty(),
                title = title,
                url = currentLine,
                duration = duration,
                licenseType = kodiMetadata[KODI_LICENSE_TYPE],
                licenseKey = kodiMetadata[KODI_LICENSE_KEY],
            )

            infoMatch = null
            kodiMatches.clear()

            entries.add(entry)
            currentCount += 1
            callback(currentCount, -1)
        }
        entries
    }
}
