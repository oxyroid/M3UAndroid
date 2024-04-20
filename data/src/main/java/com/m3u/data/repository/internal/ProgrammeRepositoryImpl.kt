package com.m3u.data.repository.internal

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
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeSnapshot
import com.m3u.data.parser.epg.EpgData
import com.m3u.data.parser.epg.EpgParser
import com.m3u.data.parser.epg.EpgProgramme
import com.m3u.data.parser.epg.toProgramme
import com.m3u.data.repository.ProgrammeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

internal class ProgrammeRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val streamDao: StreamDao,
    private val programmeDao: ProgrammeDao,
    private val epgParser: EpgParser,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
    delegate: Logger
) : ProgrammeRepository {
    private val logger = delegate.install(Profiles.REPOS_PROGRAMME)
    override val refreshingPlaylistUrls = MutableStateFlow<List<String>>(emptyList())

    override fun observeSnapshotsGroupedByPlaylistUrl(): Flow<List<ProgrammeSnapshot>> =
        programmeDao
            .observeSnapshotsGroupedByPlaylistUrl()
            .catch { emit(emptyList()) }

    override fun pagingAllByStreamId(streamId: Int): PagingSource<Int, Programme> =
        programmeDao.pagingAllByStreamId(streamId)

    override suspend fun fetchProgrammesOrThrow(playlistUrl: String) {
        if (playlistUrl in refreshingPlaylistUrls.value) return
        try {
            refreshingPlaylistUrls.value += playlistUrl
            val playlist = checkNotNull(playlistDao.getByUrl(playlistUrl))
            val epgUrl = checkNotNull(playlist.epgUrl)
            val epgData = downloadEpgOrThrow(epgUrl)
            val channels = epgData.channels
            val programmes = epgData.programmes
            val startEdge = programmes.minOfOrNull { programme ->
                programme.start?.let { EpgProgramme.readEpochMilliseconds(it) } ?: Long.MAX_VALUE
            } ?: 0L
            logger.post { "start-edge: ${Instant.fromEpochMilliseconds(startEdge)}" }
            programmeDao.clearByPlaylistUrlAndStartEdge(playlistUrl, startEdge)
            programmes.forEach { epgProgramme ->
                val channel = channels.find { it.id == epgProgramme.channel } ?: return@forEach
                val stream = streamDao.getByPlaylistUrlAndChannelId(
                    playlistUrl,
                    channel.id
                ) ?: run {
                    logger.post { "Miss stream(playlistUrl: $playlistUrl, channelId/tvg-id: ${channel.id})" }
                    return@forEach
                }
                val programme = epgProgramme.toProgramme(
                    streamId = stream.id,
                    playlistUrl = playlistUrl
                )
                val contains = programmeDao.contains(
                    programme.playlistUrl,
                    programme.streamId,
                    programme.start,
                    programme.end
                )
                if (!contains) {
                    programmeDao.insertOrReplace(programme)
                }
            }
        } finally {
            refreshingPlaylistUrls.value -= playlistUrl
        }
    }

    private suspend fun downloadEpgOrThrow(
        epgUrl: String,
    ): EpgData = withContext(ioDispatcher) {
        okHttpClient.newCall(
            Request.Builder()
                .url(epgUrl)
                .build()
        )
            .execute()
            .body
            ?.byteStream()
            ?.use { input ->
                epgParser.execute(input) { _, _ -> }
            }
            .let { checkNotNull(it) }
    }
}