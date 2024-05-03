package com.m3u.data.repository.programme

import androidx.paging.PagingSource
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.parser.epg.EpgParser
import com.m3u.data.parser.epg.EpgProgramme
import com.m3u.data.parser.epg.toProgramme
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.supervisorScope
import kotlinx.datetime.Clock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

    override fun pagingByEpgUrlsAndChannelId(
        epgUrls: List<String>,
        channelId: String
    ): PagingSource<Int, Programme> = programmeDao.pagingByEpgUrlsAndChannelId(epgUrls, channelId)

    override fun observeTimelineRange(
        epgUrls: List<String>,
        channelId: String
    ): Flow<ProgrammeRange> = programmeDao
        .observeProgrammeRange(epgUrls, channelId)
        .filterNot { (start, _) -> start == 0L }

    override suspend fun checkOrRefreshProgrammesOrThrow(
        playlistUrl: String,
        ignoreCache: Boolean
    ) {
        val playlist = playlistDao.getByUrl(playlistUrl) ?: return
        when (playlist.source) {
            DataSource.M3U -> checkOrRefreshProgrammesOrThrowImpl(playlist.epgUrls, ignoreCache)
            DataSource.Xtream -> {
                val input = XtreamInput.decodeFromPlaylistUrl(playlistUrl)
                val epgUrl = XtreamParser.createXmlUrl(
                    basicUrl = input.basicUrl,
                    username = input.username,
                    password = input.password
                )
                checkOrRefreshProgrammesOrThrowXtreamImpl(
                    epgUrl = epgUrl,
                    ignoreCache = ignoreCache
                )
            }

            else -> {}
        }
    }

    private suspend fun checkOrRefreshProgrammesOrThrowImpl(
        epgUrls: List<String>,
        ignoreCache: Boolean
    ) = coroutineScope {
        val now = Clock.System.now().toEpochMilliseconds()
        // we call it job -s because we think deferred -s is sick.
        val jobs = epgUrls.map { epgUrl ->
            async {
                if (epgUrl in refreshingEpgUrls.value) return@async
                supervisorScope {
                    try {
                        refreshingEpgUrls.value += epgUrl
                        val cacheMaxEnd = programmeDao.getMaxEndByEpgUrl(epgUrl)
                        if (!ignoreCache && cacheMaxEnd != null && cacheMaxEnd > now) return@supervisorScope

                        val epgPlaylist = playlistDao.getByUrl(epgUrl) ?: return@supervisorScope
                        check(epgPlaylist.source == DataSource.EPG) {
                            "Playlist which be queried by epgUrl is not epg source but ${epgPlaylist.source}"
                        }
                        programmeDao.cleanByEpgUrl(epgUrl)
                        downloadProgrammes(epgUrl)
                            .map { it.toProgramme(epgUrl) }
                            .collect { programme ->
                                programmeDao.insertOrReplace(programme)
                            }
                    } finally {
                        refreshingEpgUrls.value -= epgUrl
                    }
                }
            }
        }
        jobs.awaitAll()
    }

    private suspend fun checkOrRefreshProgrammesOrThrowXtreamImpl(
        epgUrl: String,
        ignoreCache: Boolean
    ): Unit = coroutineScope {
        val now = Clock.System.now().toEpochMilliseconds()
        try {
            if (epgUrl in refreshingEpgUrls.value) return@coroutineScope
            refreshingEpgUrls.value += epgUrl
            val cacheMaxEnd = programmeDao.getMaxEndByEpgUrl(epgUrl)
            if (!ignoreCache && cacheMaxEnd != null && cacheMaxEnd > now) return@coroutineScope

            programmeDao.cleanByEpgUrl(epgUrl)

            downloadProgrammes(epgUrl)
                .map { it.toProgramme(epgUrl) }
                .collect { programme ->
                    programmeDao.insertOrReplace(programme)
                }
        } finally {
            refreshingEpgUrls.value -= epgUrl
        }
    }

    private fun downloadProgrammes(epgUrl: String): Flow<EpgProgramme> = channelFlow {
        val isGzip = epgUrl
            .toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull()
            ?.endsWith(".gz", true)
            ?: false

        okHttpClient.newCall(
            Request.Builder()
                .url(epgUrl)
                .build()
        )
            .execute()
            .body
            ?.byteStream()
            ?.let {
                if (isGzip) {
                    GZIPInputStream(it).buffered()
                } else it
            }
            ?.use { input ->
                epgParser
                    .readProgrammes(input)
                    .collect { send(it) }
            }
    }
        .flowOn(ioDispatcher)
}
