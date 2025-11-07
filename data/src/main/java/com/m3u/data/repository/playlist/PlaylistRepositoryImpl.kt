package com.m3u.data.repository.playlist

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.work.WorkManager
import com.m3u.core.architecture.preferences.PlaylistStrategy
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.core.architecture.preferences.get
import com.m3u.core.util.basic.startsWithAny
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
import java.io.InputStream
import java.io.Reader
import javax.inject.Inject

private const val BUFFER_M3U_CAPACITY = 500
private const val BUFFER_XTREAM_CAPACITY = 100
private const val BUFFER_RESTORE_CAPACITY = 400

// Batch sizes for database inserts (reduced for encrypted databases)
private const val BATCH_SIZE_UNENCRYPTED = 500
private const val BATCH_SIZE_ENCRYPTED = 250
private const val TRANSACTION_DELAY_MS = 10L  // Small delay between batches for encrypted DB

internal class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    private val m3uParser: M3UParser,
    private val xtreamParser: XtreamParser,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val checksumValidator: com.m3u.data.security.ChecksumValidator
) : PlaylistRepository {
    private val timber = Timber.tag("PlaylistRepositoryImpl")

    /**
     * Helper function to insert channels in batches, optimized for encrypted databases.
     * Uses smaller batch sizes and adds delays for encrypted DBs to prevent memory issues.
     */
    private suspend fun insertChannelsBatched(channels: List<Channel>, logPrefix: String = "") {
        val isEncrypted = settings[PreferencesKeys.USB_ENCRYPTION_ENABLED]
        val batchSize = if (isEncrypted) BATCH_SIZE_ENCRYPTED else BATCH_SIZE_UNENCRYPTED

        timber.d("$logPrefix Inserting ${channels.size} channels (encrypted=$isEncrypted, batchSize=$batchSize)")

        channels.chunked(batchSize).forEachIndexed { index, batch ->
            withContext(Dispatchers.IO) {
                channelDao.insertOrReplaceAll(batch)

                // For encrypted databases, add small delay between batches to prevent memory pressure
                if (isEncrypted && index < channels.size / batchSize) {
                    kotlinx.coroutines.delay(TRANSACTION_DELAY_MS)
                }
            }

            // PERFORMANCE: Log progress every 50 batches instead of 10 to reduce logging overhead
            // Previous: index % 10 was causing excessive logging for large imports (16K+ channels)
            if (index % 50 == 0 || index == 0) {
                val progress = ((index + 1) * batchSize).coerceAtMost(channels.size)
                timber.d("$logPrefix Channel insert progress: $progress / ${channels.size}")
            }
        }

        // For large encrypted imports, suggest GC to clean up encryption buffers
        if (isEncrypted && channels.size > 5000) {
            timber.d("$logPrefix Large encrypted import complete, suggesting GC")
            System.gc()
        }

        timber.d("$logPrefix All ${channels.size} channels inserted successfully")
    }

    override suspend fun m3uOrThrow(
        title: String,
        url: String,
        callback: (count: Int) -> Unit
    ) {
        var currentCount = 0
        callback(currentCount)
        val internalUrl = url.copyToInternalDirPath()
        timber.d("m3uOrThrow: url=$url, internalUrl=$internalUrl")
        val playlistStrategy = settings[PreferencesKeys.PLAYLIST_STRATEGY]
        val favOrHiddenRelationIds = when (playlistStrategy) {
            PlaylistStrategy.ALL -> emptyList()
            else -> {
                channelDao.getFavOrHiddenRelationIdsByPlaylistUrl(url)
            }
        }
        val favOrHiddenUrls = when (playlistStrategy) {
            PlaylistStrategy.ALL -> emptyList()
            else -> {
                channelDao.getFavOrHiddenUrlsByPlaylistUrlNotContainsRelationId(url)
            }
        }

        when (playlistStrategy) {
            PlaylistStrategy.ALL -> {
                channelDao.deleteByPlaylistUrl(url)
            }

            PlaylistStrategy.KEEP -> {
                channelDao.deleteByPlaylistUrlIgnoreFavOrHidden(url)
            }
        }

        val playlist = playlistDao.get(internalUrl)?.copy(
            title = title,
            // maybe be saved as epg or any other sources.
            source = DataSource.M3U
        ) ?: Playlist(title, internalUrl, source = DataSource.M3U)
        playlistDao.insertOrReplace(playlist)

        val cache = createCoroutineCache<M3UData>(BUFFER_M3U_CAPACITY) { all ->
            try {
                timber.d("Batch insert: ${all.size} channels (total so far: $currentCount)")
                val channels = all.map { it.toChannel(internalUrl) }

                // Insert with batching optimized for encrypted databases
                insertChannelsBatched(channels, "[M3U]")

                currentCount += all.size
                callback(currentCount)
                timber.d("✓ Batch insert successful: $currentCount total channels")
            } catch (e: android.database.sqlite.SQLiteException) {
                // Database error during insert - log details and rethrow
                timber.e(e, "✗ SQLite ERROR during batch insert at count=$currentCount")
                timber.e("  Error code: ${e.message}")
                timber.e("  This may indicate:")
                timber.e("  - Encrypted database corruption")
                timber.e("  - Low memory on device")
                timber.e("  - Database file I/O error")
                throw e  // Rethrow to fail-fast and prevent partial corrupt data
            } catch (e: Exception) {
                // Unexpected error - log and rethrow
                timber.e(e, "✗ UNEXPECTED ERROR during batch insert at count=$currentCount")
                throw e
            }
        }

        channelFlow {
            timber.d("=== OPENING INPUT STREAM ===")
            timber.d("url: $url")
            timber.d("internalUrl: $internalUrl")
            timber.d("isSupportedNetworkUrl: ${url.isSupportedNetworkUrl()}")
            timber.d("isSupportedAndroidUrl: ${url.isSupportedAndroidUrl()}")

            val inputStream = when {
                url.isSupportedNetworkUrl() -> {
                    timber.d("Using openNetworkInput")
                    openNetworkInput(internalUrl)
                }
                url.isSupportedAndroidUrl() -> {
                    timber.d("Using openAndroidInput")
                    openAndroidInput(internalUrl)
                }
                else -> {
                    timber.w("No supported URL type matched!")
                    null
                }
            }

            if (inputStream == null) {
                timber.e("Failed to open input stream!")
            } else {
                timber.d("Input stream opened, available: ${inputStream.available()} bytes")
            }

            inputStream?.use { input ->
                m3uParser
                    .parse(input.buffered())
                    .filterNot {
                        val relationId = it.id
                        when {
                            relationId.isBlank() -> it.url in favOrHiddenUrls
                            else -> relationId in favOrHiddenRelationIds
                        }
                    }
                    .collect { send(it) }
            }
            close()
        }
            .onEach(cache::push)
            .onCompletion { cache.flush() }
            .flowOn(Dispatchers.IO)
            .collect()
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
            try {
                timber.d("Batch insert (Xtream): ${all.size} channels (total so far: $currentCount)")

                // Insert with batching optimized for encrypted databases
                insertChannelsBatched(all, "[Xtream]")

                currentCount += all.size
                callback(currentCount)
                timber.d("✓ Batch insert successful (Xtream): $currentCount total channels")
            } catch (e: android.database.sqlite.SQLiteException) {
                // Database error during insert - log details and rethrow
                timber.e(e, "✗ SQLite ERROR during Xtream batch insert at count=$currentCount")
                timber.e("  Error code: ${e.message}")
                timber.e("  This may indicate:")
                timber.e("  - Encrypted database corruption")
                timber.e("  - Low memory on device")
                timber.e("  - Database file I/O error")
                throw e  // Rethrow to fail-fast and prevent partial corrupt data
            } catch (e: Exception) {
                // Unexpected error - log and rethrow
                timber.e(e, "✗ UNEXPECTED ERROR during Xtream batch insert at count=$currentCount")
                throw e
            }
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
        timber.d("=== STARTING BACKUP WITH CHECKSUM VALIDATION ===")
        timber.d("Backup URI: $uri")

        val json = Json {
            prettyPrint = false
        }
        val all = playlistDao.getAllWithChannels()

        // Write backup data and calculate checksum simultaneously
        val backupChecksum = context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            // Use MessageDigest to calculate checksum while writing
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val digestOutputStream = java.security.DigestOutputStream(outputStream, digest)

            val writer = digestOutputStream.bufferedWriter()
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

            // Get final checksum
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            timber.d("✓ Backup written with checksum: $checksum")
            checksum
        }

        // Save checksum metadata alongside backup (if it's a file URI)
        if (backupChecksum != null && uri.scheme == "file") {
            try {
                val backupFile = java.io.File(uri.path ?: "")
                if (backupFile.exists()) {
                    val saved = checksumValidator.saveChecksumMetadata(backupFile, backupChecksum)
                    if (saved) {
                        timber.d("✓ Checksum metadata saved: ${backupFile.name}.checksum")
                    }
                }
            } catch (e: Exception) {
                timber.w(e, "Could not save checksum metadata (non-critical)")
            }
        }

        timber.d("=== BACKUP COMPLETE ===")
    }

    override suspend fun restoreOrThrow(uri: Uri): Unit = withContext(Dispatchers.IO) {
        timber.d("=== STARTING RESTORE WITH CHECKSUM VERIFICATION ===")
        timber.d("Restore URI: $uri")

        // ENTERPRISE SECURITY: Verify checksum before restore (if available)
        if (uri.scheme == "file") {
            try {
                val restoreFile = java.io.File(uri.path ?: "")
                if (restoreFile.exists()) {
                    timber.d("Attempting checksum verification before restore...")
                    val verification = checksumValidator.verifyBackupIntegrity(restoreFile)

                    if (verification.isCorrupted()) {
                        timber.e("✗ RESTORE ABORTED: Backup file is corrupted!")
                        timber.e("  Expected checksum: ${verification.expectedChecksum}")
                        timber.e("  Actual checksum:   ${verification.actualChecksum}")
                        throw SecurityException("Backup file is corrupted. Restore aborted to prevent data loss.")
                    }

                    if (verification.success) {
                        timber.d("✓ Checksum verification PASSED - backup is valid")
                    } else if (verification.expectedChecksum == null) {
                        timber.w("⚠ No checksum metadata found - proceeding without verification")
                    } else {
                        timber.w("⚠ Checksum verification failed: ${verification.error}")
                        timber.w("  Proceeding with caution...")
                    }
                }
            } catch (e: SecurityException) {
                // Re-throw security exceptions (corrupted backup)
                throw e
            } catch (e: Exception) {
                timber.w(e, "Could not verify checksum (non-critical)")
            }
        }

        val json = Json {
            ignoreUnknownKeys = true
        }
        val mutex = Mutex()

        // Calculate checksum while restoring to detect corruption during read
        var restoredChannelCount = 0
        var restoredPlaylistCount = 0

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val digestInputStream = java.security.DigestInputStream(inputStream, digest)
            val reader = digestInputStream.bufferedReader()

            val channels = mutableListOf<Channel>()
            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val encodedPlaylist = BackupOrRestoreContracts.unwrapPlaylist(line)
                val encodedChannel = BackupOrRestoreContracts.unwrapChannel(line)
                when {
                    encodedPlaylist != null -> {
                        val playlist = json.decodeFromString<Playlist>(encodedPlaylist)
                        playlistDao.insertOrReplace(playlist)
                        restoredPlaylistCount++
                    }

                    encodedChannel != null -> {
                        val channel = json.decodeFromString<Channel>(encodedChannel)
                        channels.add(channel)
                        if (channels.size >= BUFFER_RESTORE_CAPACITY) {
                            mutex.withLock {
                                if (channels.size >= BUFFER_RESTORE_CAPACITY) {
                                    // Insert with batching optimized for encrypted databases
                                    insertChannelsBatched(channels, "[Restore]")
                                    restoredChannelCount += channels.size
                                    channels.clear()
                                }
                            }
                        }
                    }

                    else -> {}
                }
            }
            mutex.withLock {
                // Insert remaining channels with batching optimized for encrypted databases
                if (channels.isNotEmpty()) {
                    insertChannelsBatched(channels, "[Restore-Final]")
                    restoredChannelCount += channels.size
                }
            }

            // Get checksum of data that was read
            val restoreChecksum = digest.digest().joinToString("") { "%02x".format(it) }
            timber.d("✓ Restore complete:")
            timber.d("  Playlists: $restoredPlaylistCount")
            timber.d("  Channels:  $restoredChannelCount")
            timber.d("  Checksum:  $restoreChecksum")
        }

        timber.d("=== RESTORE COMPLETE ===")
    }

    override suspend fun pinOrUnpinCategory(url: String, category: String) {
        playlistDao.updatePinnedCategories(url) { prev ->
            if (category in prev) prev - category
            else prev + category
        }
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

    private suspend fun String.copyToInternalDirPath(): String {
        if (!isSupportedAndroidUrl()) return this
        val uri = this.toUri()
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.toString()
        return withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val filename = uri.readFileName(contentResolver) ?: filenameWithTimezone
            val destinationFile = File(context.filesDir, filename)

            val success = uri.copyToFile(contentResolver, destinationFile)
            if (!success) {
                return@withContext this@copyToInternalDirPath
            }

            val newUrl = Uri.decode(destinationFile.toUri().toString())
            playlistDao.updateUrl(this@copyToInternalDirPath, newUrl)
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
        val uri = url.toUri()
        return context.contentResolver.openInputStream(uri)
    }
}