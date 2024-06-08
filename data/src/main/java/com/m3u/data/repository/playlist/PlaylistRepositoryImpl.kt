package com.m3u.data.repository.playlist

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.install
import com.m3u.core.architecture.logger.post
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.architecture.preferences.PlaylistStrategy
import com.m3u.core.architecture.preferences.Preferences
import com.m3u.core.util.basic.startsWithAny
import com.m3u.core.util.readFileContent
import com.m3u.core.util.readFileName
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithChannels
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.fromLocal
import com.m3u.data.parser.m3u.M3UData
import com.m3u.data.parser.m3u.M3UParser
import com.m3u.data.parser.m3u.toChannel
import com.m3u.data.parser.xtream.XtreamChannelInfo
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamLive
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.data.parser.xtream.XtreamSerial
import com.m3u.data.parser.xtream.XtreamVod
import com.m3u.data.parser.xtream.asChannel
import com.m3u.data.parser.xtream.toChannel
import com.m3u.data.repository.BackupOrRestoreContracts
import com.m3u.data.repository.createCoroutineCache
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream
import java.io.Reader
import javax.inject.Inject

private const val BUFFER_M3U_CAPACITY = 500
private const val BUFFER_XTREAM_CAPACITY = 100
private const val BUFFER_RESTORE_CAPACITY = 400

internal class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    delegate: Logger,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    private val m3uParser: M3UParser,
    private val xtreamParser: XtreamParser,
    private val preferences: Preferences,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
    @Dispatcher(M3uDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {
    private val logger = delegate.install(Profiles.REPOS_PLAYLIST)

    override suspend fun m3uOrThrow(
        title: String,
        url: String,
        callback: (count: Int) -> Unit
    ) {
        var currentCount = 0
        callback(currentCount)
        val actualUrl = url.actualUrl()
        logger.post {
            """
                url: $url
                actualUrl: $actualUrl
            """.trimIndent()
        }
        val favOrHiddenOriginalIds = when (preferences.playlistStrategy) {
            PlaylistStrategy.ALL -> emptyList()
            else -> {
                channelDao.getFavOrHiddenOriginalIdsByPlaylistUrl(url)
            }
        }
        val favOrHiddenUrls = when (preferences.playlistStrategy) {
            PlaylistStrategy.ALL -> emptyList()
            else -> {
                channelDao.getFavOrHiddenUrlsByPlaylistUrlNotContainsOriginalId(url)
            }
        }

        when (preferences.playlistStrategy) {
            PlaylistStrategy.ALL -> {
                channelDao.deleteByPlaylistUrl(url)
            }

            PlaylistStrategy.KEEP -> {
                channelDao.deleteByPlaylistUrlIgnoreFavOrHidden(url)
            }
        }

        val playlist = playlistDao.get(actualUrl)?.copy(
            title = title,
            // maybe be saved as epg or any other sources.
            source = DataSource.M3U
        ) ?: Playlist(title, actualUrl, source = DataSource.M3U)
        playlistDao.insertOrReplace(playlist)

        val cache = createCoroutineCache<M3UData>(BUFFER_M3U_CAPACITY) { all ->
            channelDao.insertOrReplaceAll(*all.map { it.toChannel(actualUrl) }.toTypedArray())
            currentCount += all.size
            callback(currentCount)
        }

        channelFlow {
            when {
                url.isSupportedNetworkUrl() -> openNetworkInput(actualUrl)
                url.isSupportedAndroidUrl() -> openAndroidInput(actualUrl)
                else -> null
            }?.use { input ->
                m3uParser
                    .parse(input.buffered())
                    .filterNot {
                        val originalId = it.id
                        when {
                            originalId.isBlank() -> it.url in favOrHiddenUrls
                            else -> originalId in favOrHiddenOriginalIds
                        }
                    }
                    .collect { send(it) }
            }
            close()
        }
            .onEach(cache::push)
            .onCompletion { cache.flush() }
            .flowOn(ioDispatcher)
            .collect()
    }

    override suspend fun xtreamOrThrow(
        title: String,
        basicUrl: String,
        username: String,
        password: String,
        type: String?,
        callback: (count: Int) -> Unit
    ): Unit = withContext(ioDispatcher) {
        val input = XtreamInput(basicUrl, username, password, type)
        val (
            liveCategories,
            vodCategories,
            serialCategories,
            allowedOutputFormats,
            serverProtocol,
            port
        ) = xtreamParser.getXtreamOutput(input)

        // we like ts but not m3u8.
        val liveContainerExtension = if ("ts" in allowedOutputFormats) "ts"
        else allowedOutputFormats.firstOrNull() ?: "ts"

        val livePlaylist = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_LIVE),
            serverProtocol = serverProtocol,
            port = port
        ).let { url ->
            playlistDao.get(url)
                ?.takeIf { it.source == DataSource.Xtream }
                ?.copy(
                    title = title
                )
                ?: Playlist(
                    title = title,
                    url = url,
                    source = DataSource.Xtream
                )
        }
        val vodPlaylist = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_VOD),
            serverProtocol = serverProtocol,
            port = port
        ).let { url ->
            playlistDao.get(url)
                ?.takeIf { it.source == DataSource.Xtream }
                ?.copy(
                    title = title
                )
                ?: Playlist(
                    title = title,
                    url = url,
                    source = DataSource.Xtream
                )
        }
        val seriesPlaylist = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_SERIES),
            serverProtocol = serverProtocol,
            port = port
        ).let { url ->
            playlistDao.get(url)
                ?.takeIf { it.source == DataSource.Xtream }
                ?.copy(
                    title = title
                )
                ?: Playlist(
                    title = title,
                    url = url,
                    source = DataSource.Xtream
                )
        }

        val favOrHiddenOriginalIds = channelDao.getFavOrHiddenOriginalIdsByPlaylistUrl(
            livePlaylist.url,
            vodPlaylist.url,
            seriesPlaylist.url
        )

        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES
        if (requiredLives) {
            when (preferences.playlistStrategy) {
                PlaylistStrategy.ALL -> {
                    channelDao.deleteByPlaylistUrl(livePlaylist.url)
                }

                PlaylistStrategy.KEEP -> {
                    channelDao.deleteByPlaylistUrlIgnoreFavOrHidden(livePlaylist.url)
                }
            }
            playlistDao.insertOrReplace(livePlaylist)
        }
        if (requiredVods) {
            when (preferences.playlistStrategy) {
                PlaylistStrategy.ALL -> {
                    channelDao.deleteByPlaylistUrl(vodPlaylist.url)
                }

                PlaylistStrategy.KEEP -> {
                    channelDao.deleteByPlaylistUrlIgnoreFavOrHidden(vodPlaylist.url)
                }
            }
            playlistDao.insertOrReplace(vodPlaylist)
        }
        if (requiredSeries) {
            when (preferences.playlistStrategy) {
                PlaylistStrategy.ALL -> {
                    channelDao.deleteByPlaylistUrl(seriesPlaylist.url)
                }

                PlaylistStrategy.KEEP -> {
                    channelDao.deleteByPlaylistUrlIgnoreFavOrHidden(seriesPlaylist.url)
                }
            }
            playlistDao.insertOrReplace(seriesPlaylist)
        }

        var currentCount = 0
        callback(currentCount)

        val cache = createCoroutineCache(BUFFER_XTREAM_CAPACITY) { all ->
            currentCount += all.size
            callback(currentCount)
            channelDao.insertOrReplaceAll(*all.toTypedArray())
        }

        xtreamParser
            .parse(input)
            .mapNotNull { current ->
                when (current) {
                    is XtreamLive -> {
                        val favOrHidden = with(current.streamId) {
                            val originalId = this.toString()
                            this != null && originalId in favOrHiddenOriginalIds
                        }
                        if (favOrHidden) return@mapNotNull null
                        current.toChannel(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = livePlaylist.url,
                            category = liveCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty(),
                            containerExtension = liveContainerExtension
                        )
                    }

                    is XtreamVod -> {
                        val favOrHidden = with(current.streamId) {
                            val originalId = this.toString()
                            this != null && originalId in favOrHiddenOriginalIds
                        }
                        if (favOrHidden) return@mapNotNull null
                        current.toChannel(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = vodPlaylist.url,
                            category = vodCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty()
                        )
                    }

                    // we save serial as channel
                    // when we click the serial channel, we should call serialInfo api
                    // for its episodes.
                    is XtreamSerial -> {
                        val favOrHidden = with(current.seriesId) {
                            val originalId = this.toString()
                            this != null && originalId in favOrHiddenOriginalIds
                        }
                        if (favOrHidden) return@mapNotNull null
                        current.asChannel(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = seriesPlaylist.url,
                            category = serialCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty()
                        )
                    }
                }
            }
            .onEach(cache::push)
            .onCompletion { cache.flush() }
            .collect()
    }

    override suspend fun insertEpgAsPlaylist(title: String, epg: String): Unit =
        withContext(ioDispatcher) {
            // just save epg playlist to db
            playlistDao.insertOrReplace(
                Playlist(
                    title = title,
                    url = epg,
                    source = DataSource.EPG
                )
            )
        }

    override suspend fun refresh(url: String) = logger.sandBox {
        val playlist = checkNotNull(get(url)) { "Cannot find playlist: $url" }
        check(!playlist.fromLocal) { "refreshing is not needed for local storage playlist." }

        when (playlist.source) {
            DataSource.M3U -> {
                SubscriptionWorker.m3u(workManager, playlist.title, url)
            }

            DataSource.EPG -> {
                SubscriptionWorker.epg(workManager, url, true)
            }

            DataSource.Xtream -> {
                val xtreamInput = XtreamInput.decodeFromPlaylistUrl(url)
                SubscriptionWorker.xtream(
                    workManager = workManager,
                    title = playlist.title,
                    url = url,
                    basicUrl = xtreamInput.basicUrl,
                    username = xtreamInput.username,
                    password = xtreamInput.password
                )
            }

            else -> throw IllegalStateException("Refresh data source ${playlist.source} is unsupported currently.")
        }
    }

    override suspend fun backupOrThrow(uri: Uri): Unit = withContext(ioDispatcher) {
        val json = Json {
            prettyPrint = false
        }
        val all = playlistDao.getAllWithChannels()
        context.contentResolver.openOutputStream(uri)?.use {
            val writer = it.bufferedWriter()
            all.forEach { (playlist, channels) ->
                if (playlist.fromLocal) {
                    logger.log("The playlist is from local storage, skipped. ($playlist)")
                    return@forEach
                }
                logger.sandBox {
                    val encodedPlaylist = json.encodeToString(playlist)
                    val wrappedPlaylist = BackupOrRestoreContracts.wrapPlaylist(encodedPlaylist)
                    writer.appendLine(wrappedPlaylist)
                }

                channels.forEach { channel ->
                    logger.sandBox {
                        val encodedChannel = json.encodeToString(channel)
                        val wrappedChannel = BackupOrRestoreContracts.wrapChannel(encodedChannel)
                        logger.log(wrappedChannel)
                        writer.appendLine(wrappedChannel)
                    }
                }
            }
            writer.flush()
        }
    }

    override suspend fun restoreOrThrow(uri: Uri) = logger.sandBox {
        withContext(ioDispatcher) {
            val json = Json {
                ignoreUnknownKeys = true
            }
            val mutex = Mutex()
            context.contentResolver.openInputStream(uri)?.use {
                val reader = it.bufferedReader()

                val channels = mutableListOf<Channel>()
                reader.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    val encodedPlaylist = BackupOrRestoreContracts.unwrapPlaylist(line)
                    val encodedChannel = BackupOrRestoreContracts.unwrapChannel(line)
                    when {
                        encodedPlaylist != null -> logger.sandBox {
                            val playlist = json.decodeFromString<Playlist>(encodedPlaylist)
                            playlistDao.insertOrReplace(playlist)
                        }

                        encodedChannel != null -> logger.sandBox {
                            val channel = json.decodeFromString<Channel>(encodedChannel)
                            channels.add(channel)
                            if (channels.size >= BUFFER_RESTORE_CAPACITY) {
                                mutex.withLock {
                                    if (channels.size >= BUFFER_RESTORE_CAPACITY) {
                                        channelDao.insertOrReplaceAll(*channels.toTypedArray())
                                        channels.clear()
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                }
                mutex.withLock {
                    channelDao.insertOrReplaceAll(*channels.toTypedArray())
                }
            }
        }
    }

    override suspend fun pinOrUnpinCategory(url: String, category: String) = logger.sandBox {
        playlistDao.updatePinnedCategories(url) { prev ->
            if (category in prev) prev - category
            else prev + category
        }
    }

    override suspend fun hideOrUnhideCategory(url: String, category: String) = logger.sandBox {
        playlistDao.hideOrUnhideCategory(url, category)
    }

    override fun observeAll(): Flow<List<Playlist>> = playlistDao
        .observeAll()
        .catch {
            logger.log(it)
            emit(emptyList())
        }

    override fun observeAllEpgs(): Flow<List<Playlist>> = playlistDao
        .observeAllEpgs()
        .catch {
            logger.log(it)
            emit(emptyList())
        }

    override fun observePlaylistUrls(): Flow<List<String>> = playlistDao
        .observePlaylistUrls()
        .catch {
            logger.log(it)
            emit(emptyList())
        }

    override fun observe(url: String): Flow<Playlist?> = playlistDao
        .observeByUrl(url)
        .catch {
            logger.log(it)
            emit(null)
        }

    override fun observePlaylistWithChannels(url: String): Flow<PlaylistWithChannels?> = playlistDao
        .observeByUrlWithChannels(url)
        .catch {
            logger.log(it)
            emit(null)
        }

    override suspend fun getPlaylistWithChannels(url: String): PlaylistWithChannels? =
        logger.execute {
            playlistDao.getByUrlWithChannels(url)
        }

    override suspend fun get(url: String): Playlist? = logger.execute {
        playlistDao.get(url)
    }

    override suspend fun getAll(): List<Playlist> = logger.execute {
        playlistDao.getAll()
    } ?: emptyList()

    override suspend fun getAllAutoRefresh(): List<Playlist> = logger.execute {
        playlistDao.getAllAutoRefresh()
    } ?: emptyList()

    override suspend fun getBySource(source: DataSource): List<Playlist> = logger.execute {
        playlistDao.getBySource(source)
    } ?: emptyList()

    override suspend fun getCategoriesByPlaylistUrlIgnoreHidden(
        url: String,
        query: String
    ): List<String> = playlistDao.get(url).let { playlist ->
        val pinnedCategories = playlist?.pinnedCategories ?: emptyList()
        val hiddenCategories = playlist?.hiddenCategories ?: emptyList()
        channelDao
            .getCategoriesByPlaylistUrl(url, query)
            .filterNot { it in hiddenCategories }
            .sortedByDescending { it in pinnedCategories }
    }

    override fun observeCategoriesByPlaylistUrlIgnoreHidden(
        url: String,
        query: String
    ): Flow<List<String>> = playlistDao.observeByUrl(url).flatMapLatest { playlist ->
        playlist ?: return@flatMapLatest flowOf()
        val pinnedCategories = playlist.pinnedCategories
        val hiddenCategories = playlist.hiddenCategories
        channelDao
            .observeCategoriesByPlaylistUrl(playlist.url, query)
            .map { categories ->
                categories
                    .filterNot { it in hiddenCategories }
                    .sortedByDescending { it in pinnedCategories }
            }
    }

    override suspend fun unsubscribe(url: String): Playlist? = logger.execute {
        val playlist = playlistDao.get(url)
        channelDao.deleteByPlaylistUrl(url)
        playlist?.also {
            playlistDao.delete(it)
        }
    }

    override suspend fun onUpdatePlaylistTitle(url: String, title: String) = logger.sandBox {
        playlistDao.updateTitle(url, title)
    }

    override suspend fun onUpdatePlaylistUserAgent(url: String, userAgent: String?) =
        logger.sandBox {
            playlistDao.updateUserAgent(url, userAgent)
        }

    override fun observeAllCounts(): Flow<List<PlaylistWithCount>> =
        playlistDao.observeAllCounts()
            .catch { emit(emptyList()) }

    override suspend fun readEpisodesOrThrow(series: Channel): List<XtreamChannelInfo.Episode> {
        val playlist = checkNotNull(get(series.playlistUrl)) { "playlist is not exist" }
        val seriesInfo = xtreamParser.getSeriesInfoOrThrow(
            input = XtreamInput.decodeFromPlaylistUrl(playlist.url),
            seriesId = Url(series.url).pathSegments.last().toInt()
        )
        // fixme: do not flatmap
        return seriesInfo.episodes.flatMap { it.value }
    }

    override suspend fun deleteEpgPlaylistAndProgrammes(epgUrl: String) {
        playlistDao.deleteByUrl(epgUrl)
        programmeDao.deleteAllByEpgUrl(epgUrl)
        playlistDao.removeEpgUrlForAllPlaylists(epgUrl)
    }

    override suspend fun onUpdateEpgPlaylist(useCase: PlaylistRepository.UpdateEpgPlaylistUseCase) {
        val playlist = checkNotNull(playlistDao.get(useCase.playlistUrl)) {
            "Cannot find playlist before update associated epg"
        }
        val epg = checkNotNull(playlistDao.get(useCase.epgUrl)) {
            "Cannot find associated epg"
        }

        playlistDao.updateEpgUrls(playlist.url) { epgUrls ->
            if (useCase.action) epgUrls + epg.url
            else epgUrls - epg.url
        }
    }

    override suspend fun onUpdatePlaylistAutoRefreshProgrammes(playlistUrl: String) {
        val playlist = playlistDao.get(playlistUrl) ?: return
        playlistDao.updatePlaylistAutoRefreshProgrammes(
            playlistUrl,
            !playlist.autoRefreshProgrammes
        )
    }

    private val filenameWithTimezone: String get() = "File_${System.currentTimeMillis()}"

    // Modified with `inline`
    private inline fun Reader.forEachLine(action: (String) -> Unit): Unit =
        useLines { it.forEach(action) }

    private fun String.isSupportedNetworkUrl(): Boolean = startsWithAny(
        "http://",
        "https://",
        ignoreCase = true
    )

    private fun String.isSupportedAndroidUrl(): Boolean = startsWithAny(
        ContentResolver.SCHEME_FILE,
        ContentResolver.SCHEME_CONTENT,
        ignoreCase = true
    )

    private suspend fun String.actualUrl(): String {
        if (!isSupportedAndroidUrl()) return this
        val uri = Uri.parse(this)
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.toString()
        return withContext(ioDispatcher) {
            val contentResolver = context.contentResolver
            val filename = uri.readFileName(contentResolver) ?: filenameWithTimezone
            val content = uri.readFileContent(contentResolver).orEmpty()
            val file = File(context.filesDir, filename)
            file.writeText(content)

            val newUrl = Uri.decode(file.toUri().toString())
            playlistDao.updateUrl(this@actualUrl, newUrl)
            newUrl
        }
    }

    private fun openNetworkInput(url: String): InputStream? {
        val request = Request.Builder()
            .url(url)
            .build()
        val response = okHttpClient.newCall(request).execute()
        return response.body?.byteStream()
    }

    private fun openAndroidInput(url: String): InputStream? {
        val uri = Uri.parse(url)
        return context.contentResolver.openInputStream(uri)
    }
}