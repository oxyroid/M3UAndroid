package com.m3u.data.repository.programme

import androidx.paging.PagingSource
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.post
import com.m3u.core.util.basic.letIf
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.parser.epg.EpgParser
import com.m3u.data.parser.epg.EpgProgramme
import com.m3u.data.parser.epg.toProgramme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.zip.GZIPInputStream
import javax.inject.Inject

internal class ProgrammeRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val programmeDao: ProgrammeDao,
    private val epgParser: EpgParser,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    delegate: Logger
) : ProgrammeRepository {
    private val logger = delegate.install(Profiles.REPOS_PROGRAMME)
    override val refreshingEpgUrls = MutableStateFlow<List<String>>(emptyList())

    override fun pagingByEpgUrlsAndOriginalId(
        epgUrls: List<String>,
        originalId: String
    ): PagingSource<Int, Programme> = programmeDao.pagingByEpgUrlsAndOriginalId(epgUrls, originalId)

    override fun observeProgrammeRange(
        playlistUrl: String,
        originalId: String
    ): Flow<ProgrammeRange> = playlistDao.observeByUrl(playlistUrl).flatMapLatest { playlist ->
        playlist ?: return@flatMapLatest flowOf()
        programmeDao
            .observeProgrammeRange(playlist.epgUrlsOrXtreamXmlUrl(), originalId)
            .filterNot { (start, end) -> start == 0L || end == 0L }
    }

    override fun observeProgrammeRange(playlistUrl: String): Flow<ProgrammeRange> =
        playlistDao.observeByUrl(playlistUrl)
            .map { playlist ->
                playlist?.epgUrlsOrXtreamXmlUrl() ?: emptyList()
            }
            .flatMapLatest { epgUrls ->
                programmeDao.observeProgrammeRange(epgUrls)
            }

    override fun checkOrRefreshProgrammesOrThrow(
        vararg playlistUrls: String,
        ignoreCache: Boolean
    ): Flow<Int> = channelFlow {
        val epgUrls = playlistUrls.flatMap { playlistUrl ->
            val playlist = playlistDao.get(playlistUrl) ?: return@flatMap emptyList()
            playlist.epgUrlsOrXtreamXmlUrl()
        }
            .toSet()
            .toList()
        val producer = checkOrRefreshProgrammesOrThrowImpl(
            epgUrls = epgUrls,
            ignoreCache = ignoreCache
        )
        var count = 0
        producer.collect { programme ->
            programmeDao.insertOrReplace(programme)
            send(++count)
        }
    }

    override suspend fun getById(id: Int): Programme? = logger.execute {
        programmeDao.getById(id)
    }

    private fun checkOrRefreshProgrammesOrThrowImpl(
        epgUrls: List<String>,
        ignoreCache: Boolean
    ): Flow<Programme> = channelFlow {
        val now = Clock.System.now().toEpochMilliseconds()
        // we call it job -s because we think deferred -s is sick.
        val jobs = epgUrls.map { epgUrl ->
            async {
                if (epgUrl in refreshingEpgUrls.value) run {
                    logger.post { "skipped! epgUrl is refreshing. [$epgUrl]" }
                    return@async
                }
                supervisorScope {
                    try {
                        refreshingEpgUrls.value += epgUrl
                        val cacheMaxEnd = programmeDao.getMaxEndByEpgUrl(epgUrl)
                        if (!ignoreCache && cacheMaxEnd != null && cacheMaxEnd > now) run {
                            logger.post { "skipped! exist validate programmes. [$epgUrl]" }
                            return@supervisorScope
                        }

                        programmeDao.cleanByEpgUrl(epgUrl)
                        downloadProgrammes(epgUrl)
                            .map { it.toProgramme(epgUrl) }
                            .collect { send(it) }
                    } finally {
                        refreshingEpgUrls.value -= epgUrl
                    }
                }
            }
        }
        jobs.awaitAll()
    }

    private fun downloadProgrammes(epgUrl: String): Flow<EpgProgramme> = channelFlow {
        val request = Request.Builder()
            .url(epgUrl)
            .build()

        val response = okHttpClient.newCall(request).execute()
        val url = response.request.url
        val contentType = response.header("Content-Type").orEmpty()

        val isGzip = "gzip" in contentType ||
                // soft rule, cover the situation which with wrong MIME_TYPE(text, octect etc.)
                url.pathSegments.lastOrNull()?.endsWith(".gz") == true

        response
            .body
            ?.byteStream()
            ?.letIf(isGzip) { GZIPInputStream(it).buffered() }
            ?.use { input ->
                epgParser
                    .readProgrammes(input)
                    .collect { send(it) }
            }
    }
        .flowOn(ioDispatcher)
}
