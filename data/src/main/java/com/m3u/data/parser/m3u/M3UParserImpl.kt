package com.m3u.data.parser.m3u

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.post
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import javax.inject.Inject

internal class M3UParserImpl @Inject constructor(
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    delegate: Logger
) : M3UParser {
    private val logger = delegate.install(Profiles.PARSER_M3U)

    companion object {
        private const val M3U_HEADER_MARK = "#EXTM3U"
        private const val M3U_INFO_MARK = "#EXTINF:"
        private const val KODI_MARK = "#KODIPROP:"

        private val infoRegex = """(-?\d+)(.*),(.+)""".toRegex()
        private val kodiPropRegex = """([^=]+)=(.+)""".toRegex()
        private val metadataRegex = """([\w-_.]+)=\s*(?:"([^"]*)"|(\S+))""".toRegex()

        private const val M3U_TVG_LOGO_MARK = "tvg-logo"
        const val M3U_TVG_ID_MARK = "tvg-id"
        const val M3U_TVG_NAME_MARK = "tvg-name"
        const val M3U_GROUP_TITLE_MARK = "group-title"

        const val KODI_LICENSE_TYPE = "inputstream.adaptive.license_type"
        const val KODI_LICENSE_KEY = "inputstream.adaptive.license_key"
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
        var infoMatch: MatchResult? = null
        val kodiMatches = mutableListOf<MatchResult>()

        while (lines.hasNext()) {
            currentLine = lines.next()
            while (currentLine.startsWith("#")) {
                logger.post { currentLine }
                if (currentLine.startsWith(M3U_INFO_MARK)) {
                    infoMatch = infoRegex
                        .matchEntire(currentLine.drop(M3U_INFO_MARK.length).trim())
                }
                if (currentLine.startsWith(KODI_MARK)) {
                    kodiPropRegex
                        .matchEntire(currentLine.drop(KODI_MARK.length).trim())
                        ?.also { kodiMatches += it }
                }
                if (lines.hasNext()) {
                    currentLine = lines.next()
                }
            }
            if (infoMatch == null && !currentLine.startsWith("#")) continue

            val title = infoMatch?.groups?.get(3)?.value.orEmpty().trim()
            val duration = infoMatch?.groups?.get(1)?.value?.toDouble() ?: -1.0
            val metadata = buildMap {
                val text = infoMatch?.groups?.get(2)?.value.orEmpty().trim()
                val matches = metadataRegex.findAll(text)
                for (match in matches) {
                    val key = match.groups[1]!!.value
                    val value = match.groups[2]?.value?.ifBlank { null } ?: continue
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

            emit(entry)
        }
    }
        .flowOn(ioDispatcher)
}
