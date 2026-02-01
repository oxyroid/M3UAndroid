package com.m3u.data.repository.programme

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.m3u.core.util.basic.letIf
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.parser.epg.EpgParser
import com.m3u.data.parser.epg.EpgProgramme
import com.m3u.data.parser.epg.toProgramme
import kotlinx.coroutines.Dispatchers
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
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class ProgrammeRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    private val epgParser: EpgParser,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
) : ProgrammeRepository {
    private val timber = Timber.tag("ProgrammeRepositoryImpl")
    override val refreshingEpgUrls = MutableStateFlow<List<String>>(emptyList())

    override fun pagingProgrammes(
        playlistUrl: String,
        relationId: String
    ): Flow<PagingData<Programme>> = playlistDao
        .observeByUrl(playlistUrl)
        .map { playlist -> playlist?.epgUrlsOrXtreamXmlUrl() ?: emptyList() }
        .map { epgUrls -> findValidEpgUrl(epgUrls, relationId, defaultProgrammeRange) }
        .flatMapLatest { epgUrl ->
            Pager(PagingConfig(15)) { programmeDao.pagingProgrammes(epgUrl, relationId) }.flow
        }

    override fun observeProgrammeRange(
        playlistUrl: String,
        relationId: String
    ): Flow<ProgrammeRange> = playlistDao.observeByUrl(playlistUrl)
        .map { playlist -> playlist?.epgUrlsOrXtreamXmlUrl() ?: emptyList() }
        .map { epgUrls -> findValidEpgUrl(epgUrls, relationId, defaultProgrammeRange) }
        .flatMapLatest { epgUrl ->
            epgUrl ?: return@flatMapLatest flowOf()
            programmeDao
                .observeProgrammeRange(epgUrl, relationId)
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

    private val defaultProgrammeRange: ProgrammeRange
        get() = with(Clock.System.now()) {
            ProgrammeRange(
                this.minus(1.days).toEpochMilliseconds(),
                this.plus(1.days).toEpochMilliseconds()
            )
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

    override suspend fun getById(id: Int): Programme? = programmeDao.getById(id)

    override suspend fun getProgrammeCurrently(channelId: Int): Programme? {
        val channel = channelDao.get(channelId) ?: return null
        val relationId = channel.relationId ?: return null
        val playlist = playlistDao.get(channel.playlistUrl) ?: return null

        val epgUrls = playlist.epgUrlsOrXtreamXmlUrl()
        if (epgUrls.isEmpty()) return null

        val time = Clock.System.now().toEpochMilliseconds()
        return programmeDao.getCurrentByEpgUrlsAndRelationId(
            epgUrls = epgUrls,
            relationId = relationId,
            time = time
        )
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
                    timber.d("skipped! epgUrl is refreshing. [$epgUrl]")
                    return@async
                }
                supervisorScope {
                    try {
                        refreshingEpgUrls.value += epgUrl
                        val cacheMaxEnd = programmeDao.getMaxEndByEpgUrl(epgUrl)
                        if (!ignoreCache && cacheMaxEnd != null && cacheMaxEnd > now) run {
                            timber.d("skipped! exist validate programmes. [$epgUrl]")
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
            .byteStream()
            .letIf(isGzip) { GZIPInputStream(it).buffered() }
            .use { input ->
                epgParser
                    .readProgrammes(input)
                    .collect { send(it) }
            }
    }
        .flowOn(Dispatchers.IO)

    /**
     * Attempts to find the first valid EPG URL from a list of URLs.
     *
     * This function iterates over the provided list of EPG URLs and checks
     * if each URL is valid by querying from the database. The validity check
     * uses the `relationId` and the start and end times from the `ProgrammeRange`.
     * The first valid EPG URL found is returned. If no valid URLs are found,
     * the function returns null.
     *
     * @param epgUrls A list of EPG URLs to check.
     * @param relationId A unique identifier representing the relation for the EPG.
     * @param range A `ProgrammeRange` object containing the start and end times to validate against.
     * @return The first valid EPG URL, or null if none are valid.
     */
    private suspend fun findValidEpgUrl(
        epgUrls: List<String>,
        relationId: String,
        range: ProgrammeRange
    ): String? = epgUrls.firstOrNull { epgUrl ->
        programmeDao.checkEpgUrlIsValid(epgUrl, relationId, range.start, range.end)
    }
}
