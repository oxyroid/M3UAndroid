package com.m3u.data.repository.programme

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.m3u.core.foundation.util.basic.letIf
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.epgUrlsOrXtreamXmlUrl
import com.m3u.data.parser.epg.EpgParser
import com.m3u.data.parser.epg.EpgProgramme
import com.m3u.data.parser.epg.toProgramme
import com.m3u.data.repository.BoundedJsonlRecordStaging
import com.m3u.data.repository.playlist.EpgDataMaintenanceCoordinator
import com.m3u.data.repository.playlist.PlaylistDataMaintenanceCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class ProgrammeRepositoryImpl @Inject constructor(
    private val database: M3UDatabase,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    private val epgParser: EpgParser,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context,
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
        PlaylistDataMaintenanceCoordinator.withExclusive {
            val ownerPlaylistUrls = playlistUrls.toList()
            val epgUrls = ownerPlaylistUrls.flatMap { playlistUrl ->
                val playlist = playlistDao.get(playlistUrl) ?: return@flatMap emptyList()
                playlist.epgUrlsOrXtreamXmlUrl()
            }
                .toSet()
                .toList()
            checkOrRefreshProgrammesOrThrowImpl(
                ownerPlaylistUrls = ownerPlaylistUrls,
                epgUrls = epgUrls,
                ignoreCache = ignoreCache,
                onProgramme = { count -> send(count) },
            ).collect {}
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

    override suspend fun getProgrammesCurrently(playlistUrl: String): Map<String, Programme> {
        val playlist = playlistDao.get(playlistUrl) ?: return emptyMap()
        val epgUrls = playlist.epgUrlsOrXtreamXmlUrl()
        if (epgUrls.isEmpty()) return emptyMap()

        val time = Clock.System.now().toEpochMilliseconds()
        return programmeDao.getCurrentByPlaylistUrlAndEpgUrls(
            playlistUrl = playlistUrl,
            epgUrls = epgUrls,
            time = time
        ).associateBy { it.channelId }
    }

    private fun checkOrRefreshProgrammesOrThrowImpl(
        ownerPlaylistUrls: List<String>,
        epgUrls: List<String>,
        ignoreCache: Boolean,
        onProgramme: suspend (Int) -> Unit,
    ): Flow<Unit> = channelFlow {
        val now = Clock.System.now().toEpochMilliseconds()
        val count = AtomicInteger()
        // we call it job -s because we think deferred -s is sick.
        val jobs = epgUrls.map { epgUrl ->
            async {
                EpgDataMaintenanceCoordinator.withExclusive(epgUrl) {
                    val stillReferenced = ownerPlaylistUrls.any { playlistUrl ->
                        playlistDao.get(playlistUrl)
                            ?.epgUrlsOrXtreamXmlUrl()
                            ?.contains(epgUrl) == true
                    }
                    if (!stillReferenced) return@withExclusive
                    try {
                        refreshingEpgUrls.update { refreshing -> refreshing + epgUrl }
                        val cacheMaxEnd = programmeDao.getMaxEndByEpgUrl(epgUrl)
                        if (!ignoreCache && cacheMaxEnd != null && cacheMaxEnd > now) run {
                            timber.d("EPG refresh skipped because cached programmes are valid")
                            return@withExclusive
                        }

                        val staging = stageProgrammes(
                            epgUrl = epgUrl,
                            onProgramme = {
                                onProgramme(count.incrementAndGet())
                            },
                        )
                        try {
                            database.withTransaction {
                                programmeDao.cleanByEpgUrl(epgUrl)
                                staging.forEachBatch(EPG_INSERT_BATCH_SIZE) { programmes ->
                                    programmeDao.insertOrReplaceAll(
                                        *programmes.toTypedArray()
                                    )
                                }
                            }
                        } finally {
                            staging.close()
                        }
                    } finally {
                        refreshingEpgUrls.update { refreshing -> refreshing - epgUrl }
                    }
                }
            }
        }
        jobs.awaitAll()
    }

    private suspend fun stageProgrammes(
        epgUrl: String,
        onProgramme: suspend () -> Unit,
    ): BoundedJsonlRecordStaging<Programme> {
        val staging = BoundedJsonlRecordStaging.create(
            cacheDirectory = File(context.cacheDir, EPG_STAGING_DIRECTORY),
            limits = EPG_STAGING_LIMITS,
            encode = { programme ->
                EPG_STAGING_JSON.encodeToString(Programme.serializer(), programme)
            },
            decode = { encoded ->
                EPG_STAGING_JSON.decodeFromString(Programme.serializer(), encoded)
            },
        )
        return try {
            withContext(Dispatchers.IO) {
                downloadProgrammes(epgUrl)
                    .map { it.toProgramme(epgUrl) }
                    .collect { programme ->
                        staging.append(programme)
                        onProgramme()
                    }
                currentCoroutineContext().ensureActive()
                staging.seal()
            }
            staging
        } catch (error: Throwable) {
            staging.close()
            throw error
        }
    }

    private fun downloadProgrammes(epgUrl: String): Flow<EpgProgramme> = channelFlow {
        val request = Request.Builder()
            .url(epgUrl)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("EPG request failed with HTTP ${response.code}")
            }
            val url = response.request.url
            val contentType = response.header("Content-Type").orEmpty()
            val isGzip = "gzip" in contentType ||
                // Some servers return a generic or incorrect MIME type.
                url.pathSegments.lastOrNull()?.endsWith(".gz") == true

            response.body
                .byteStream()
                .letIf(isGzip) { GZIPInputStream(it).buffered() }
                .use { input ->
                    epgParser
                        .readProgrammes(input)
                        .collect { send(it) }
                }
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

    private companion object {
        const val EPG_INSERT_BATCH_SIZE = 1_000
        const val EPG_STAGING_DIRECTORY = "epg-import-staging"
        val EPG_STAGING_LIMITS = BoundedJsonlRecordStaging.Limits(
            maximumRecords = 1_000_000,
            maximumBytes = 512L * 1024L * 1024L,
            maximumRecordBytes = 1024 * 1024,
        )
        val EPG_STAGING_JSON = Json {
            encodeDefaults = true
            ignoreUnknownKeys = false
        }
    }
}
