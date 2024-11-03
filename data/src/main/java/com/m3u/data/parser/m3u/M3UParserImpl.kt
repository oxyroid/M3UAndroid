package com.m3u.data.parser.m3u

import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.post
import com.m3u.extension.api.analyzer.HlsPropAnalyzer
import com.m3u.extension.api.analyzer.HlsPropAnalyzer.Companion.MAX_COUNT_HLS_PROP_ANALYZER
import com.m3u.extension.api.analyzer.HlsPropAnalyzer.Companion.TOTAL_MAX_COUNT_HLS_PROP_ANALYZER
import com.m3u.extension.api.analyzer.HlsPropAnalyzer.HlsResult.LicenseKey
import com.m3u.extension.api.analyzer.HlsPropAnalyzer.HlsResult.LicenseType
import com.m3u.extension.api.analyzer.HlsPropAnalyzer.HlsResult.NotHandled
import com.m3u.extension.api.analyzer.HlsPropAnalyzer.HlsResult.UserAgent
import com.m3u.extension.runtime.ExtensionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import javax.inject.Inject

internal class M3UParserImpl @Inject constructor(
    private val extensionManager: ExtensionManager,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    logger: Logger
) : M3UParser {
    private val logger = logger.install(Profiles.PARSER_M3U)

    companion object {
        private const val M3U_HEADER_MARK = "#EXTM3U"
        private const val M3U_INFO_MARK = "#EXTINF:"

        private val infoRegex = """(-?\d+)(.*),(.+)""".toRegex()
        private val propRegex = """#([^:]+):([^=]+)=(.*)""".toRegex()
        private val metadataRegex = """([\w-_.]+)=\s*(?:"([^"]*)"|(\S+))""".toRegex()

        private const val M3U_TVG_LOGO_MARK = "tvg-logo"
        const val M3U_TVG_ID_MARK = "tvg-id"
        const val M3U_TVG_NAME_MARK = "tvg-name"
        const val M3U_GROUP_TITLE_MARK = "group-title"
    }

    override fun parse(input: InputStream): Flow<M3UData> = flow {
        val extensions by lazy { extensionManager.extensions.value }
        val propAnalyzers by lazy {
            extensions
                .asSequence()
                .flatMap {
                    it.analyzers
                        .filterIsInstance<HlsPropAnalyzer>()
                        .take(MAX_COUNT_HLS_PROP_ANALYZER)
                }
                .take(TOTAL_MAX_COUNT_HLS_PROP_ANALYZER)
        }
        val lines = input
            .bufferedReader()
            .lineSequence()
            .filter { it.isNotEmpty() }
            .map { it.trimEnd() }
            .dropWhile { it.startsWith(M3U_HEADER_MARK) }
            .iterator()

        var currentLine: String
        var infoMatch: MatchResult? = null
        val hlsResults = mutableListOf<HlsPropAnalyzer.HlsResult>()

        while (lines.hasNext()) {
            currentLine = lines.next()
            while (currentLine.startsWith("#")) {
                logger.post { currentLine }
                when {
                    currentLine.startsWith(M3U_INFO_MARK) -> {
                        infoMatch = infoRegex.matchEntire(currentLine.drop(M3U_INFO_MARK.length).trim())
                    }

                    else -> {
                        val matchEntire = propRegex.matchEntire(currentLine.trim()) ?: continue
                        val groups = matchEntire.groups
                        val protocol = groups[1]?.value
                        val key = groups[2]?.value
                        val value = groups[3]?.value.orEmpty()
                        if (protocol.isNullOrEmpty() || key.isNullOrEmpty()) continue
                        for (analyzer in propAnalyzers) {
                            val result = try {
                                analyzer.onAnalyze(protocol, key, value)
                            } catch (e: Throwable) {
                                logger.post { e.stackTraceToString() }
                                continue
                            }
                            if (result == NotHandled) continue
                            hlsResults += result
                        }
                    }
                }
                if (lines.hasNext()) {
                    currentLine = lines.next()
                }
            }
            if (infoMatch == null && !currentLine.startsWith("#")) continue

            val groups = infoMatch?.groups
            val title = groups?.get(3)?.value.orEmpty().trim()
            val duration = groups?.get(1)?.value?.toDouble() ?: -1.0

            val metadata = buildMap {
                val text = groups?.get(2)?.value.orEmpty().trim()
                val matches = metadataRegex.findAll(text)
                for (match in matches) {
                    val innerGroups = match.groups
                    val key = innerGroups[1]?.value?.ifBlank { null } ?: continue
                    val value = innerGroups[2]?.value?.ifBlank { null } ?: continue
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
                licenseType = hlsResults.filterIsInstance<LicenseType>().firstOrNull()?.value,
                licenseKey = hlsResults.filterIsInstance<LicenseKey>().firstOrNull()?.value,
                userAgent = hlsResults.filterIsInstance<UserAgent>().firstOrNull()?.value
            )

            infoMatch = null
            hlsResults.clear()

            emit(entry)
        }
    }
        .flowOn(ioDispatcher)
}
