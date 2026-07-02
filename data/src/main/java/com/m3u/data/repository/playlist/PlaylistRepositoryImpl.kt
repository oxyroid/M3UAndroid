package com.m3u.data.repository.playlist

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.m3u.core.architecture.preferences.PlaylistStrategy
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.get
import com.m3u.core.util.copyToFile
import com.m3u.core.util.readFileName
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithChannels
import com.m3u.data.database.model.refreshable
import com.m3u.data.database.model.toMap
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
import kotlinx.coroutines.Dispatchers
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
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import java.io.StringReader
import java.io.StringWriter
import java.io.Writer
import javax.inject.Inject

private const val BUFFER_M3U_CAPACITY = 500
private const val BUFFER_XTREAM_CAPACITY = 100
private const val BUFFER_RESTORE_CAPACITY = 400

internal class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    private val m3uParser: M3UParser,
    private val xtreamParser: XtreamParser,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
    private val settings: Settings
) : PlaylistRepository {
    private val timber = Timber.tag("PlaylistRepositoryImpl")

    override suspend fun m3uOrThrow(
        title: String,
        url: String,
        callback: (count: Int) -> Unit
    ) {
        var currentCount = 0
        callback(currentCount)
        val normalizedUrl = PlaylistNetworkUrl.normalizeM3uInput(url)
        val internalUrl = normalizedUrl.copyToInternalDirPath()
        timber.d("m3uOrThrow: url=$url, internalUrl=$internalUrl")
        val input = withContext(Dispatchers.IO) {
            when {
                PlaylistNetworkUrl.isSupportedNetworkUrl(normalizedUrl) -> openNetworkInput(internalUrl)
                PlaylistNetworkUrl.isSupportedAndroidUrl(normalizedUrl) -> {
                    openAndroidInput(internalUrl) ?: error("Failed to open playlist: $internalUrl")
                }

                else -> error("Unsupported playlist url: $normalizedUrl")
            }
        }
        val playlistStrategy = settings[PreferencesKeys.PLAYLIST_STRATEGY]
        val favOrHiddenRelationIds = when (playlistStrategy) {
            PlaylistStrategy.ALL -> emptyList()
            else -> {
                channelDao.getFavOrHiddenRelationIdsByPlaylistUrl(internalUrl)
            }
        }
        val favOrHiddenUrls = when (playlistStrategy) {
            PlaylistStrategy.ALL -> emptyList()
            else -> {
                channelDao.getFavOrHiddenUrlsByPlaylistUrlNotContainsRelationId(internalUrl)
            }
        }

        when (playlistStrategy) {
            PlaylistStrategy.ALL -> {
                channelDao.deleteByPlaylistUrl(internalUrl)
            }

            PlaylistStrategy.KEEP -> {
                channelDao.deleteByPlaylistUrlIgnoreFavOrHidden(internalUrl)
            }
        }

        val playlist = playlistDao.get(internalUrl)?.copy(
            title = title,
            // maybe be saved as epg or any other sources.
            source = DataSource.M3U
        ) ?: Playlist(title, internalUrl, source = DataSource.M3U)
        playlistDao.insertOrReplace(playlist)

        val cache = createCoroutineCache<M3UData>(BUFFER_M3U_CAPACITY) { all ->
            channelDao.insertOrReplaceAll(*all.map { it.toChannel(internalUrl) }.toTypedArray())
            currentCount += all.size
            callback(currentCount)
        }
        var playlistHeaderEpgUrlsApplied = false

        input.use { openedInput ->
            channelFlow {
                m3uParser
                    .parse(openedInput.buffered())
                    .onEach { data ->
                        if (!playlistHeaderEpgUrlsApplied && data.playlistEpgUrls.isNotEmpty()) {
                            playlistHeaderEpgUrlsApplied = true
                            playlistDao.updateEpgUrls(internalUrl) { epgUrls ->
                                (epgUrls + data.playlistEpgUrls).distinct()
                            }
                        }
                    }
                    .filterNot {
                        val relationId = it.id
                        when {
                            relationId.isBlank() -> it.url in favOrHiddenUrls
                            else -> relationId in favOrHiddenRelationIds
                        }
                    }
                    .collect { data -> send(data) }
                close()
            }
                .onEach(cache::push)
                .onCompletion { cache.flush() }
                .flowOn(Dispatchers.IO)
                .collect()
        }
    }

    override suspend fun xtreamOrThrow(
        title: String,
        basicUrl: String,
        username: String,
        password: String,
        type: String?,
        callback: (count: Int) -> Unit
    ): Unit = withContext(Dispatchers.IO) {
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

        val favOrHiddenRelationIds = channelDao.getFavOrHiddenRelationIdsByPlaylistUrl(
            livePlaylist.url,
            vodPlaylist.url,
            seriesPlaylist.url
        )

        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES

        val playlistStrategy = settings[PreferencesKeys.PLAYLIST_STRATEGY]

        if (requiredLives) {
            when (playlistStrategy) {
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
            when (playlistStrategy) {
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
            when (playlistStrategy) {
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
                            val relationId = this.toString()
                            this != null && relationId in favOrHiddenRelationIds
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
                            val relationId = this.toString()
                            this != null && relationId in favOrHiddenRelationIds
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
                            val relationId = this.toString()
                            this != null && relationId in favOrHiddenRelationIds
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

    override suspend fun insertEpgAsPlaylist(title: String, epg: String) {
        // just save epg playlist to db
        playlistDao.insertOrReplace(
            Playlist(
                title = title,
                url = epg,
                source = DataSource.EPG
            )
        )
    }

    override suspend fun refresh(url: String) {
        val playlist = get(url) ?: run {
            timber.w("Playlist not found for url: $url")
            return
        }
        if (!playlist.refreshable) {
            timber.w("Playlist is not refreshable: $playlist")
            return
        }

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

    override suspend fun backupOrThrow(uri: Uri): Unit = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use {
            backupToWriter(it.bufferedWriter())
        }
    }

    override suspend fun backupAsTextOrThrow(): String = withContext(Dispatchers.IO) {
        StringWriter().use { writer ->
            backupToWriter(writer)
            writer.toString()
        }
    }

    override suspend fun restoreOrThrow(uri: Uri): Unit = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use {
            restoreFromReader(it.bufferedReader())
        }
    }

    override suspend fun restoreOrThrow(text: String): Unit = withContext(Dispatchers.IO) {
        StringReader(text).use { reader ->
            restoreFromReader(reader)
        }
    }

    private suspend fun backupToWriter(writer: Writer) {
        val json = Json {
            prettyPrint = false
        }
        val all = playlistDao.getAllWithChannels()
        writer.use {
            all.forEach { (playlist, channels) ->
                val encodedPlaylist = json.encodeToString(playlist)
                val wrappedPlaylist = BackupOrRestoreContracts.wrapPlaylist(encodedPlaylist)
                writer.appendLine(wrappedPlaylist)

                channels.forEach { channel ->
                    val encodedChannel = json.encodeToString(channel)
                    val wrappedChannel = BackupOrRestoreContracts.wrapChannel(encodedChannel)
                    writer.appendLine(wrappedChannel)
                }
            }
            writer.flush()
        }
    }

    private suspend fun restoreFromReader(reader: Reader) {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val mutex = Mutex()
        reader.use {
            val channels = mutableListOf<Channel>()
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val encodedPlaylist = BackupOrRestoreContracts.unwrapPlaylist(line)
                val encodedChannel = BackupOrRestoreContracts.unwrapChannel(line)
                when {
                    encodedPlaylist != null -> {
                        val playlist = json.decodeFromString<Playlist>(encodedPlaylist)
                        playlistDao.insertOrReplace(playlist)
                    }

                    encodedChannel != null -> {
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

    override suspend fun pinOrUnpinCategory(url: String, category: String) {
        playlistDao.updatePinnedCategories(url) { prev ->
            if (category in prev) prev - category
            else prev + category
        }
    }

    override suspend fun reorderCategories(url: String, categories: List<String>) {
        playlistDao.updateOrderedCategories(url, categories)
    }

    override suspend fun hideOrUnhideCategory(url: String, category: String) {
        playlistDao.hideOrUnhideCategory(url, category)
    }

    override fun observeAll(): Flow<List<Playlist>> = playlistDao
        .observeAll()
        .catch { emit(emptyList()) }

    override fun observeAllEpgs(): Flow<List<Playlist>> = playlistDao
        .observeAllEpgs()
        .catch { emit(emptyList()) }

    override fun observePlaylistUrls(): Flow<List<String>> = playlistDao
        .observePlaylistUrls()
        .catch { emit(emptyList()) }

    override fun observe(url: String): Flow<Playlist?> = playlistDao
        .observeByUrl(url)
        .catch { emit(null) }

    override fun observePlaylistWithChannels(url: String): Flow<PlaylistWithChannels?> = playlistDao
        .observeByUrlWithChannels(url)
        .catch { emit(null) }

    override suspend fun getPlaylistWithChannels(url: String): PlaylistWithChannels? = playlistDao.getByUrlWithChannels(url)

    override suspend fun get(url: String): Playlist? = playlistDao.get(url)

    override suspend fun getAll(): List<Playlist> = playlistDao.getAll()

    override suspend fun getAllAutoRefresh(): List<Playlist> = playlistDao.getAllAutoRefresh()

    override suspend fun getBySource(source: DataSource): List<Playlist> = playlistDao.getBySource(source)

    override suspend fun getCategoriesByPlaylistUrlIgnoreHidden(
        url: String,
        query: String
    ): List<String> = playlistDao.get(url).let { playlist ->
        val pinnedCategories = playlist?.pinnedCategories ?: emptyList()
        val hiddenCategories = playlist?.hiddenCategories ?: emptyList()
        val orderedCategories = playlist?.orderedCategories ?: emptyList()
        channelDao
            .getCategoriesByPlaylistUrl(url, query)
            .orderPlaylistCategories(
                orderedCategories = orderedCategories,
                pinnedCategories = pinnedCategories,
                hiddenCategories = hiddenCategories
            )
    }

    override fun observeCategoriesByPlaylistUrlIgnoreHidden(
        url: String,
        query: String
    ): Flow<List<String>> = playlistDao.observeByUrl(url).flatMapLatest { playlist ->
        playlist ?: return@flatMapLatest flowOf()
        val pinnedCategories = playlist.pinnedCategories
        val hiddenCategories = playlist.hiddenCategories
        val orderedCategories = playlist.orderedCategories
        channelDao
            .observeCategoriesByPlaylistUrl(playlist.url, query)
            .map { categories ->
                categories
                    .orderPlaylistCategories(
                        orderedCategories = orderedCategories,
                        pinnedCategories = pinnedCategories,
                        hiddenCategories = hiddenCategories
                    )
            }
    }
        .flowOn(Dispatchers.Default)

    override suspend fun unsubscribe(url: String): Playlist? {
        val playlist = playlistDao.get(url)
        channelDao.deleteByPlaylistUrl(url)
        return playlist?.also {
            playlistDao.delete(it)
        }
    }

    override suspend fun onUpdatePlaylistTitle(url: String, title: String) = playlistDao.updateTitle(url, title)

    override suspend fun onUpdatePlaylistUserAgent(url: String, userAgent: String?) = playlistDao.updateUserAgent(url, userAgent)

    override fun observeAllCounts(): Flow<Map<Playlist, Int>> = playlistDao.observeAllCounts()
            .map { it.toMap() }
            .catch { emit(emptyMap()) }

    override suspend fun readEpisodesOrThrow(series: Channel): List<XtreamChannelInfo.Episode> {
        val playlist = checkNotNull(get(series.playlistUrl)) { "playlist is not exist" }
        val seriesInfo = xtreamParser.getSeriesInfoOrThrow(
            input = XtreamInput.decodeFromPlaylistUrl(playlist.url),
            seriesId = Url(series.url).rawSegments.last().toInt()
        )
        // fixme: do not flatmap
        return seriesInfo.episodes.flatMap { it.value }
    }

    override suspend fun deleteEpgPlaylistAndProgrammes(epgUrl: String) {
        playlistDao.deleteByUrl(epgUrl)
        programmeDao.deleteAllByEpgUrl(epgUrl)
        playlistDao.removeEpgUrlForAllPlaylists(epgUrl)
    }

    override suspend fun onUpdateEpgPlaylist(useCase: PlaylistRepository.EpgPlaylistUseCase) {
        when (useCase) {
            is PlaylistRepository.EpgPlaylistUseCase.Check -> {
                playlistDao.updateEpgUrls(useCase.playlistUrl) { epgUrls ->
                    if (useCase.action) epgUrls + useCase.epgUrl
                    else epgUrls - useCase.epgUrl
                }
            }

            is PlaylistRepository.EpgPlaylistUseCase.Upgrade -> {
                val epgUrl = useCase.epgUrl
                playlistDao.updateEpgUrls(useCase.playlistUrl) { epgUrls ->
                    val index = epgUrls.indexOf(epgUrl)
                    if (index <= 0) epgUrls
                    else with(epgUrls) {
                        take(index - 1) + epgUrl + this[index - 1] + drop(index + 1)
                    }
                }
            }
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

    private suspend fun String.copyToInternalDirPath(): String {
        if (!PlaylistNetworkUrl.isSupportedAndroidUrl(this)) return this
        val uri = this.toUri()
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return PlaylistNetworkUrl.normalizeAndroidFileUrl(uri.toString())
        }
        uri.asOwnFilesProviderFile()
            ?.takeIf { it.isFile }
            ?.let { file ->
                return PlaylistNetworkUrl.normalizeAndroidFileUrl(file.toUri().toString())
            }
        return withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val filename = PlaylistNetworkUrl.resolveInternalFileName(
                uri = uri,
                displayName = uri.readFileName(contentResolver),
                fallbackName = filenameWithTimezone
            )
            val destinationFile = File(context.filesDir, filename)

            val success = uri.copyToFile(contentResolver, destinationFile)
            if (!success) {
                return@withContext this@copyToInternalDirPath
            }

            val newUrl = PlaylistNetworkUrl.normalizeAndroidFileUrl(destinationFile.toUri().toString())
            playlistDao.updateUrl(this@copyToInternalDirPath, newUrl)
            newUrl
        }
    }

    private fun Uri.asOwnFilesProviderFile(): File? {
        if (scheme != ContentResolver.SCHEME_CONTENT) return null
        val relativePath = PlaylistNetworkUrl.resolveOwnFilesProviderRelativePath(
            authority = authority,
            pathSegments = pathSegments,
            packageName = context.packageName
        ) ?: return null
        return File(context.filesDir, relativePath)
    }


    private fun openNetworkInput(url: String): InputStream {
        val request = Request.Builder()
            .url(url)
            .build()
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (error: IOException) {
            val fallbackUrl = PlaylistNetworkUrl.httpFallbackForPlainHttpTlsFailure(
                url = url,
                responseMessage = error.message.orEmpty(),
                responseBody = error.cause?.message.orEmpty()
            )
            if (fallbackUrl != null) {
                timber.w(error, "Retrying M3U playlist over HTTP after TLS exception: $fallbackUrl")
                return openNetworkInput(fallbackUrl)
            }
            throw error
        }
        if (response.isSuccessful) {
            return response.body.byteStream()
        }

        val responseBody = response.body.string()
        val fallbackUrl = PlaylistNetworkUrl.httpFallbackForPlainHttpTlsFailure(
            url = url,
            responseMessage = response.message,
            responseBody = responseBody
        )
        response.close()
        if (fallbackUrl != null) {
            timber.w("Retrying M3U playlist over HTTP after TLS failure: $fallbackUrl")
            return openNetworkInput(fallbackUrl)
        }

        val message = response.message.takeIf { it.isNotBlank() } ?: responseBody
        error("Failed to fetch playlist: HTTP ${response.code} $message")
    }

    private fun openAndroidInput(url: String): InputStream? {
        val uri = url.toUri()
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            runCatching { return uri.toFile().inputStream() }
                .onFailure { timber.w(it, "Failed to open file playlist directly: $url") }
        }
        return context.contentResolver.openInputStream(uri)
    }
}
