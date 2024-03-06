package com.m3u.data.repository.internal

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.prefix
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.util.basic.startsWithAny
import com.m3u.core.util.readFileContent
import com.m3u.core.util.readFileName
import com.m3u.data.api.xtream.toStream
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.StreamDao
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithStreams
import com.m3u.data.database.model.Stream
import com.m3u.data.parser.M3UParser
import com.m3u.data.parser.XtreamInput
import com.m3u.data.parser.XtreamParser
import com.m3u.data.parser.toStream
import com.m3u.data.repository.PlaylistRepository
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.i18n.R.string
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.Reader
import javax.inject.Inject

class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val streamDao: StreamDao,
    logger: Logger,
    private val client: OkHttpClient,
    private val m3uParser: M3UParser,
    private val xtreamParser: XtreamParser,
    private val pref: Pref,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {
    private val logger = logger.prefix("playlist-repos")

    override suspend fun m3u(
        title: String,
        url: String,
        callback: (count: Int, total: Int) -> Unit
    ) {
        suspend fun parse(
            playlistUrl: String,
            input: InputStream
        ): List<Stream> = logger.execute {
            m3uParser.execute(input).map { it.toStream(playlistUrl, 0L) }
        } ?: emptyList()

        suspend fun acquireNetwork(url: String): List<Stream> {
            val request = Request.Builder()
                .url(url)
                .build()
            val response = withContext(ioDispatcher) {
                client.newCall(request).execute()
            }
            if (!response.isSuccessful) return emptyList()
            val input = response.body?.byteStream()
            return input?.use { parse(url, it) } ?: emptyList()
        }

        suspend fun acquireAndroid(url: String): List<Stream> {
            val uri = Uri.parse(url)
            val input = context.contentResolver.openInputStream(uri)
            return input?.use { parse(url, it) } ?: emptyList()
        }

        var currentCount = 0
        callback(currentCount, -1)
        withContext(ioDispatcher) {
            try {
                val actualUrl = url.actualUrl()
                val streams = when {
                    url.isSupportedNetworkUrl() -> acquireNetwork(actualUrl)
                    url.isSupportedAndroidUrl() -> acquireAndroid(actualUrl)
                    else -> emptyList() // never reach here!
                }
                currentCount += streams.size
                callback(currentCount, -1)

                val playlist = Playlist(title, actualUrl)
                playlistDao.insertOrReplace(playlist)

                streamDao.compareAndUpdate(
                    strategy = pref.playlistStrategy,
                    url = url,
                    update = streams
                )
            } catch (e: FileNotFoundException) {
                error(context.getString(string.data_error_file_not_found))
            } catch (e: Exception) {
                logger.log(e)
            }
        }
    }

    override suspend fun xtream(
        title: String,
        basicUrl: String,
        username: String,
        password: String,
        type: String?,
        callback: (count: Int, total: Int) -> Unit
    ) = withContext(ioDispatcher) {
        val input = XtreamInput(basicUrl, username, password, type)
        val (
            lives,
            vods,
            series,
            liveCategories,
            vodCategories,
            serialCategories,
            allowedOutputFormats,
            serverProtocol,
            port
        ) = xtreamParser.execute(input)

        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES

        val total = run {
            var i = 0
            if (requiredLives) i += lives.size
            if (requiredVods) i += vods.size
            if (requiredSeries) i += series.size
            i
        }
        var currentCount = 0
        callback(currentCount, total)

        if (requiredLives) {
            val playlist = Playlist(
                title = title,
                url = XtreamInput.encodeToPlaylistUrl(
                    input = input.copy(type = DataSource.Xtream.TYPE_LIVE),
                    serverProtocol = serverProtocol,
                    port = port
                ),
                source = DataSource.Xtream
            )
            playlistDao.insertOrReplace(playlist)
            val streams = lives.map { current ->
                current.toStream(
                    basicUrl = basicUrl,
                    username = username,
                    password = password,
                    playlistUrl = playlist.url,
                    category = liveCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty(),
                    containerExtension = allowedOutputFormats.first()
                ).also {
                    currentCount += 1
                    callback(currentCount, total)
                }
            }
            streamDao.compareAndUpdate(
                strategy = pref.playlistStrategy,
                url = playlist.url,
                update = streams
            )
            logger.log("xtream: lives +[${streams.size}]")
        }
        if (requiredVods) {
            val playlist = Playlist(
                title = title,
                url = XtreamInput.encodeToPlaylistUrl(
                    input = input.copy(type = DataSource.Xtream.TYPE_VOD),
                    serverProtocol = serverProtocol,
                    port = port
                ),
                source = DataSource.Xtream
            )
            playlistDao.insertOrReplace(playlist)
            val streams = vods.map { current ->
                current.toStream(
                    basicUrl = basicUrl,
                    username = username,
                    password = password,
                    playlistUrl = playlist.url,
                    category = vodCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty()
                ).also {
                    currentCount += 1
                    callback(currentCount, total)
                }
            }
            streamDao.compareAndUpdate(
                strategy = pref.playlistStrategy,
                url = playlist.url,
                update = streams
            )
            logger.log("xtream: vods +[${vods.size}]")
        }

        if (requiredSeries) {
            val playlist = Playlist(
                title = title,
                url = XtreamInput.encodeToPlaylistUrl(
                    input = input.copy(type = DataSource.Xtream.TYPE_SERIES),
                    serverProtocol = serverProtocol,
                    port = port
                ),
                source = DataSource.Xtream
            )
            playlistDao.insertOrReplace(playlist)
            val streams = series.flatMap { current ->
                ensureActive()
                val seriesInfo = xtreamParser.getSeriesInfo(
                    input = input.copy(type = DataSource.Xtream.TYPE_SERIES),
                    seriesId = current.seriesId ?: return@flatMap emptyList()
                ) ?: return@flatMap emptyList()
                seriesInfo.episodes.flatMap { (_, episodes) ->
                    episodes.map { episode ->
                        Stream(
                            url = "$basicUrl/series/$username/$password/${episode.id}.${episode.containerExtension}",
                            category = serialCategories.find { it.categoryId == current.categoryId }?.categoryName.orEmpty(),
                            title = current.name.orEmpty() + " " + episode.title.orEmpty(),
                            cover = current.cover,
                            playlistUrl = playlist.url,
                        )
                    }
                }.also {
                    currentCount += 1
                    callback(currentCount, total)
                }
            }
            streamDao.compareAndUpdate(
                strategy = pref.playlistStrategy,
                url = playlist.url,
                update = streams
            )
            logger.log("xtream: series +[${streams.size}]")
        }
    }

    override suspend fun refresh(url: String) = logger.sandBox {
        val playlist = checkNotNull(get(url)) { "Cannot find playlist: $url" }
        check(!playlist.fromLocal) { "refreshing is not needed for local storage playlist." }

        when (playlist.source) {
            DataSource.M3U -> {
                workManager.cancelAllWorkByTag(url)
                val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                    .setInputData(
                        workDataOf(
                            SubscriptionWorker.INPUT_STRING_TITLE to playlist.title,
                            SubscriptionWorker.INPUT_STRING_URL to url,
                            SubscriptionWorker.INPUT_STRING_DATA_SOURCE_VALUE to DataSource.M3U.value
                        )
                    )
                    .addTag(url)
                    .addTag(SubscriptionWorker.TAG)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                workManager.enqueue(request)
            }

            DataSource.Xtream -> {
                val input = XtreamInput.decodeFromPlaylistUrlOrNull(playlist.url) ?: return@sandBox
                workManager.cancelAllWorkByTag(url)
                workManager.cancelAllWorkByTag(input.basicUrl)
                val request = OneTimeWorkRequestBuilder<SubscriptionWorker>()
                    .setInputData(
                        workDataOf(
                            SubscriptionWorker.INPUT_STRING_TITLE to playlist.title,
                            SubscriptionWorker.INPUT_STRING_URL to url,
                            SubscriptionWorker.INPUT_STRING_BASIC_URL to input.basicUrl,
                            SubscriptionWorker.INPUT_STRING_USERNAME to input.username,
                            SubscriptionWorker.INPUT_STRING_PASSWORD to input.password,
                            SubscriptionWorker.INPUT_STRING_DATA_SOURCE_VALUE to DataSource.Xtream.value
                        )
                    )
                    .addTag(url)
                    .addTag(input.basicUrl)
                    .addTag(SubscriptionWorker.TAG)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                workManager.enqueue(request)
            }

            else -> throw RuntimeException("Refresh data source ${playlist.source} is unsupported currently.")
        }
    }

    override suspend fun backupOrThrow(uri: Uri): Unit = withContext(ioDispatcher) {
        val json = Json {
            prettyPrint = false
        }
        val all = playlistDao.getAllWithStreams()
        context.contentResolver.openOutputStream(uri)?.use {
            val writer = it.bufferedWriter()
            all.forEach { (playlist, streams) ->
                if (playlist.fromLocal) {
                    logger.log("The playlist is from local storage, skipped. ($playlist)")
                    return@forEach
                }
                logger.sandBox {
                    val encodedPlaylist = json.encodeToString(playlist)
                    val wrappedPlaylist = BackupOrRestoreContracts.wrapPlaylist(encodedPlaylist)
                    writer.appendLine(wrappedPlaylist)
                }

                streams.forEach { stream ->
                    logger.sandBox {
                        val encodedStream = json.encodeToString(stream)
                        val wrappedStream = BackupOrRestoreContracts.wrapStream(encodedStream)
                        logger.log(wrappedStream)
                        writer.appendLine(wrappedStream)
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
            context.contentResolver.openInputStream(uri)?.use {
                val reader = it.bufferedReader()

                val playlists = mutableListOf<Playlist>()
                val streams = mutableListOf<Stream>()
                reader.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    val encodedPlaylist = BackupOrRestoreContracts.unwrapPlaylist(line)
                    val encodedStream = BackupOrRestoreContracts.unwrapStream(line)
                    when {
                        encodedPlaylist != null -> logger.sandBox {
                            val playlist = json.decodeFromString<Playlist>(encodedPlaylist)
                            playlists.add(playlist)
                        }

                        encodedStream != null -> logger.sandBox {
                            val stream = json.decodeFromString<Stream>(encodedStream)
                            streams.add(stream)
                        }

                        else -> {}
                    }
                }
                playlistDao.insertOrReplaceAll(*playlists.toTypedArray())
                streamDao.insertOrReplaceAll(*streams.toTypedArray())
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

    override fun observe(url: String): Flow<Playlist?> = playlistDao
        .observeByUrl(url)
        .catch {
            logger.log(it)
            emit(null)
        }

    override fun observeWithStreams(url: String): Flow<PlaylistWithStreams?> = playlistDao
        .observeByUrlWithStreams(url)
        .catch {
            logger.log(it)
            emit(null)
        }

    override suspend fun getWithStreams(url: String): PlaylistWithStreams? = logger.execute {
        playlistDao.getByUrlWithStreams(url)
    }

    override suspend fun get(url: String): Playlist? = logger.execute {
        playlistDao.getByUrl(url)
    }

    override suspend fun unsubscribe(url: String): Playlist? = logger.execute {
        val playlist = playlistDao.getByUrl(url)
        streamDao.deleteByPlaylistUrl(url)
        playlist?.also {
            playlistDao.delete(it)
        }
    }

    override suspend fun rename(url: String, target: String) = logger.sandBox {
        playlistDao.rename(url, target)
    }

    override suspend fun updateUserAgent(url: String, userAgent: String?) = logger.sandBox {
        playlistDao.updateUserAgent(url, userAgent)
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
        return when {
            isSupportedNetworkUrl() -> this
            isSupportedAndroidUrl() -> {
                val uri = Uri.parse(this)
                if (uri.scheme == ContentResolver.SCHEME_FILE) {
                    return uri.toString()
                }
                withContext(ioDispatcher) {
                    val resolver = context.contentResolver
                    val filename = uri.readFileName(resolver) ?: filenameWithTimezone
                    val content = uri.readFileContent(resolver).orEmpty()
                    val file = File(context.filesDir, filename)
                    file.writeText(content)

                    val newUrl = Uri.decode(file.toUri().toString())
                    playlistDao.updateUrl(this@actualUrl, newUrl)
                    newUrl
                }
            }

            else -> error("unsupported url scheme: $this")
        }
    }
}
