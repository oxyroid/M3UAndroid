package com.m3u.data.repository.internal

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.m3u.core.architecture.dispatcher.Dispatcher
import com.m3u.core.architecture.dispatcher.M3uDispatchers.IO
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.execute
import com.m3u.core.architecture.logger.sandBox
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.util.basic.startsWithAny
import com.m3u.core.util.belong
import com.m3u.core.util.readFileContent
import com.m3u.core.util.readFileName
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.emitException
import com.m3u.core.wrapper.emitMessage
import com.m3u.core.wrapper.emitResource
import com.m3u.core.wrapper.resourceFlow
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
import com.m3u.i18n.R.string
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
    private val logger: Logger,
    private val client: OkHttpClient,
    private val m3UParser: M3UParser,
    private val xtreamParser: XtreamParser,
    @ApplicationContext private val context: Context,
    @Dispatcher(IO) private val ioDispatcher: CoroutineDispatcher,
) : PlaylistRepository {

    override fun m3u(
        title: String,
        url: String,
        strategy: Int
    ): Flow<Resource<Unit>> = resourceFlow {
        try {
            val actualUrl = url.actualUrl()
            if (actualUrl == null) {
                emitMessage("wrong url")
                return@resourceFlow
            }
            val streams = when {
                url.isSupportedNetworkUrl -> acquireNetwork(actualUrl)
                url.isSupportedAndroidUrl -> acquireAndroid(actualUrl)
                else -> {
                    emitMessage("unsupported url")
                    return@resourceFlow
                }
            }

            val playlist = Playlist(title, actualUrl)
            playlistDao.insert(playlist)

            compareAndUpdateDB(
                previous = streamDao.getByPlaylistUrl(url),
                streams = streams,
                strategy = strategy
            )

            emitResource(Unit)
        } catch (e: FileNotFoundException) {
            error(context.getString(string.data_error_file_not_found))
        } catch (e: Exception) {
            logger.log(e)
            emitException(e)
        }
    }
        .flowOn(ioDispatcher)

    override suspend fun xtream(
        title: String,
        address: String,
        username: String,
        password: String,
        strategy: Int
    ) {
        val input = XtreamInput(address, username, password)
        val output = xtreamParser.execute(input)
        val all = output.all
        val allowedOutputFormats = output.allowedOutputFormats
        val playlist = Playlist(
            title = title,
            url = "$address/player_api.php?username=$username&password=$password",
            source = DataSource.Xtream
        )
        playlistDao.insert(playlist)
        val streams = all.map {
            Stream(
                url = "$address/$username/$password/${it.streamId}.${allowedOutputFormats.first()}",
                group = "",
                title = it.name.orEmpty(),
                cover = it.streamIcon,
                playlistUrl = playlist.url
            )
        }
        compareAndUpdateDB(
            streamDao.getByPlaylistUrl(playlist.url),
            streams,
            strategy
        )
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
                playlistDao.insertAll(*playlists.toTypedArray())
                streamDao.insertAll(*streams.toTypedArray())
            }
        }
    }

    private suspend inline fun compareAndUpdateDB(
        previous: List<Stream>,
        streams: List<Stream>,
        @PlaylistStrategy strategy: Int,
    ) {
        val skippedUrls = mutableListOf<String>()
        val grouped by lazy {
            previous.groupBy { it.favourite }.withDefault { emptyList() }
        }
        val invalidate = when (strategy) {
            PlaylistStrategy.ALL -> previous
            PlaylistStrategy.SKIP_FAVORITE -> grouped.getValue(false)
            else -> emptyList()
        }
        invalidate.forEach { stream ->
            if (stream belong streams) {
                skippedUrls += stream.url
            } else {
                streamDao.deleteByUrl(stream.url)
            }
        }
        val existedUrls = when (strategy) {
            PlaylistStrategy.ALL -> skippedUrls
            PlaylistStrategy.SKIP_FAVORITE -> grouped
                .getValue(true)
                .map { it.url } + skippedUrls

            else -> emptyList()
        }
        val needToBeInsertedStreams = streams.filterNot { it.url in existedUrls }

        streamDao.insertAll(*needToBeInsertedStreams.toTypedArray())
    }

    private suspend fun acquireNetwork(url: String): List<Stream> {
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

    private suspend fun acquireAndroid(url: String): List<Stream> {
        val uri = Uri.parse(url)
        val input = context.contentResolver.openInputStream(uri)
        return input?.use { parse(url, it) } ?: emptyList()
    }

    override fun observeAll(): Flow<List<Playlist>> = logger.execute {
        playlistDao.observeAll()
    } ?: flow { }

    override fun observe(url: String): Flow<Playlist?> = logger.execute {
        playlistDao.observeByUrl(url)
    } ?: flow { }

    override fun observeWithStreams(url: String): Flow<PlaylistWithStreams?> = logger.execute {
        playlistDao.observeByUrlWithStreams(url)
    } ?: flow { }

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

    private suspend fun parse(
        playlistUrl: String,
        input: InputStream
    ): List<Stream> = logger.execute {
        m3UParser.execute(input).map { it.toStream(playlistUrl, 0L) }
    } ?: emptyList()

    override suspend fun rename(url: String, target: String) = logger.sandBox {
        playlistDao.rename(url, target)
    }

    private val String.isSupportedNetworkUrl: Boolean
        get() = this.startsWithAny(
            "http://",
            "https://",
            ignoreCase = true
        )
    private val String.isSupportedAndroidUrl: Boolean
        get() = this.startsWithAny(
            ContentResolver.SCHEME_FILE,
            ContentResolver.SCHEME_CONTENT,
            ignoreCase = true
        )

    private suspend fun String.actualUrl(): String? {
        return if (isSupportedNetworkUrl) this
        else if (isSupportedAndroidUrl) {
            val uri = Uri.parse(this) ?: return null
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
        } else null
    }

    private val filenameWithTimezone: String get() = "File_${System.currentTimeMillis()}"

    // Modified with `inline`
    private inline fun Reader.forEachLine(action: (String) -> Unit): Unit =
        useLines { it.forEach(action) }
}
