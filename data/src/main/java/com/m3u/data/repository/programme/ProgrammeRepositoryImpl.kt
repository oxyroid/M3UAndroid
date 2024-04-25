package com.m3u.data.repository.programme

import androidx.paging.PagingSource
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.post
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Programme
import com.m3u.data.parser.epg.EpgData
import com.m3u.data.parser.epg.EpgParser
import com.m3u.data.parser.epg.EpgProgramme
import com.m3u.data.parser.epg.toProgramme
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
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

    override fun pagingByChannelId(channelId: String): PagingSource<Int, Programme> =
        programmeDao.pagingAllByChannelId(channelId)

    override suspend fun checkOrRefreshProgrammesByPlaylistUrlOrThrow(
        playlistUrl: String,
        ignoreCache: Boolean
    ): Unit = coroutineScope {
        val playlist = playlistDao.getByUrl(playlistUrl) ?: return@coroutineScope
        val now = Clock.System.now().toEpochMilliseconds()
        // we call it job -s because we think deferred -s is sick.
        val jobs = playlist.epgUrls.map { epgUrl ->
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
                        val epgData = downloadEpgOrThrow(epgUrl)
                        val programmes = epgData.programmes
                        val startEdge = programmes.minOfOrNull { programme ->
                            programme.start?.let { EpgProgramme.readEpochMilliseconds(it) }
                                ?: Long.MAX_VALUE
                        } ?: 0L
                        logger.post { "start-edge: ${Instant.fromEpochMilliseconds(startEdge)}" }
                        programmeDao.cleanByEpgUrlAndStartEdge(epgUrl, startEdge)

                        programmes
                            .asSequence()
                            .map { it.toProgramme(epgUrl) }
                            .forEach { programme ->
                                val contains = programmeDao.contains(
                                    programme.epgUrl,
                                    programme.channelId,
                                    programme.start,
                                    programme.end
                                )
                                if (!contains) {
                                    programmeDao.insertOrReplace(programme)
                                }
                            }
                    } finally {
                        refreshingEpgUrls.value -= epgUrl
                    }
                }

            }
        }
        jobs.awaitAll()
    }

    private suspend fun downloadEpgOrThrow(epgUrl: String): EpgData {
        val isGzip = epgUrl
            .toHttpUrlOrNull()
            ?.pathSegments
            ?.lastOrNull()
            ?.endsWith(".gz", true)
            ?: false
        return withContext(ioDispatcher) {
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
                    epgParser.execute(input) { _, _ -> }
                }
                .let { checkNotNull(it) }
        }
    }
}