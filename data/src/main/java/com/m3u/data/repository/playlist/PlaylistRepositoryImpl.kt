package com.m3u.data.repository.playlist

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.withTransaction
import androidx.work.WorkManager
import androidx.work.await
import com.m3u.core.foundation.architecture.preferences.PlaylistStrategy
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.get
import com.m3u.core.foundation.util.basic.PlaylistInputKind
import com.m3u.core.foundation.util.basic.normalizePlaylistInputForSubmission
import com.m3u.core.foundation.util.basic.startsWithAny
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithChannels
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.refreshable
import com.m3u.data.database.model.toMap
import com.m3u.data.parser.m3u.M3UParser
import com.m3u.data.parser.m3u.toChannel
import com.m3u.data.parser.xtream.XtreamEpisodeInfo
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.parser.xtream.XtreamLive
import com.m3u.data.parser.xtream.XtreamParser
import com.m3u.data.parser.xtream.toXtreamEpisodeInfo
import com.m3u.data.parser.xtream.XtreamSerial
import com.m3u.data.parser.xtream.XtreamVod
import com.m3u.data.parser.xtream.asChannel
import com.m3u.data.parser.xtream.toChannel
import com.m3u.data.repository.BackupStagingFiles
import com.m3u.data.repository.BackupOrRestoreContracts
import com.m3u.data.repository.BoundedJsonlRecordStaging
import com.m3u.data.repository.ProviderAccountBackup
import com.m3u.data.repository.ProviderPlaybackReferenceBackup
import com.m3u.data.repository.isProviderPlaylistNamespace
import com.m3u.data.repository.isSubscriptionProvider
import com.m3u.data.repository.isValidForRestore
import com.m3u.data.repository.selectRestorableProviderAccounts
import com.m3u.data.repository.toProviderBackupCopy
import com.m3u.data.repository.toRestorableProviderBackupCopyOrNull
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.provider.ProviderLifecycleCoordinator
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.worker.SubscriptionWorker
import com.m3u.data.worker.ProviderRefreshWorker
import com.m3u.data.worker.epgSubscriptionWorkName
import com.m3u.data.worker.hashedWorkTag
import com.m3u.data.worker.m3uSubscriptionWorkName
import com.m3u.data.worker.playlistWorkTag
import com.m3u.data.worker.xtreamPlaylistWorkTag
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.inject.Inject

private const val BUFFER_M3U_CAPACITY = 500
private const val BUFFER_XTREAM_CAPACITY = 100
private const val MAX_STAGED_CHANNELS = 200_000
private const val MAX_STAGED_CHANNEL_BYTES = 256L * 1024L * 1024L
private const val MAX_STAGED_CHANNEL_RECORD_BYTES = 1024 * 1024
private const val BUFFER_RESTORE_CAPACITY = 400
private const val MAX_RESTORED_PROVIDER_ACCOUNTS = 256
private const val MAX_RESTORED_PROVIDER_CHANNELS_PER_PLAYLIST = 50_000
private const val MAX_RESTORED_PROVIDER_CHANNELS_TOTAL = 200_000

private val CHANNEL_STAGING_LIMITS = BoundedJsonlRecordStaging.Limits(
    maximumRecords = MAX_STAGED_CHANNELS,
    maximumBytes = MAX_STAGED_CHANNEL_BYTES,
    maximumRecordBytes = MAX_STAGED_CHANNEL_RECORD_BYTES,
)
private val CHANNEL_STAGING_JSON = Json {
    encodeDefaults = true
}

private data class ProviderRestoreMetadata(
    val accountsByPlaylistUrl: Map<String, ProviderAccount>,
    val maximumOrdinaryChannelId: Int,
) {
    fun excludingConflictsWith(
        existingAccounts: List<ProviderAccount>,
    ): ProviderRestoreMetadata = ProviderRestoreMetadata(
        accountsByPlaylistUrl = selectRestorableProviderAccounts(
            incoming = accountsByPlaylistUrl.values,
            existing = existingAccounts,
        ).associateBy(ProviderAccount::playlistUrl),
        maximumOrdinaryChannelId = maximumOrdinaryChannelId,
    )
}

private data class PlaylistBackupSnapshot(
    val accounts: List<ProviderAccountBackup>,
    val playlists: List<PlaylistWithChannels>,
    val playbackReferences: List<ProviderPlaybackReferenceBackup>,
)

private data class ProviderChannelRestoreEntry(
    val backupId: Int,
    val channel: Channel,
)

private data class M3uSourceLocation(
    val internalUrl: String,
    val destinationFile: File? = null,
)

@Serializable
private data class StagedChannel(
    val channel: Channel,
    val preservationRelationId: String?,
)

internal class PlaylistRepositoryImpl @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val providerDao: ProviderDao,
    private val database: M3UDatabase,
    private val providerLifecycleCoordinator: ProviderLifecycleCoordinator,
    private val programmeDao: ProgrammeDao,
    @OkhttpClient(true) private val okHttpClient: OkHttpClient,
    private val m3uParser: M3UParser,
    private val xtreamParser: XtreamParser,
    private val workManager: WorkManager,
    @ApplicationContext private val context: Context,
    private val settings: Settings,
    private val subscriptionProviderRepository: SubscriptionProviderRepository,
    private val extensionContributionScheduler: ExtensionContributionScheduler,
    private val extensionContributionRunCoordinator: ExtensionContributionRunCoordinator,
) : PlaylistRepository {
    private val timber = Timber.tag("PlaylistRepositoryImpl")

    override suspend fun m3uOrThrow(
        title: String,
        url: String,
        callback: (count: Int) -> Unit
    ) = PlaylistDataMaintenanceCoordinator.withExclusive {
        val location = url.resolveM3uSourceLocation()
        extensionContributionRunCoordinator.withPlaylists(listOf(url, location.internalUrl)) {
            val sourceStagingFile = location.destinationFile?.let { destination ->
                url.toUri().copyToOwnedM3uStagingFile(destination)
            }
            try {
                importM3uLocked(
                    title = title,
                    sourceUrl = url,
                    inputUrl = sourceStagingFile?.toUri()?.toString()
                        ?: location.internalUrl,
                    internalUrl = location.internalUrl,
                    ownedSourceStagingFile = sourceStagingFile,
                    ownedSourceDestination = location.destinationFile,
                    callback = callback,
                )
            } finally {
                sourceStagingFile?.deleteOrTruncate()
            }
        }
    }

    private suspend fun importM3uLocked(
        title: String,
        sourceUrl: String,
        inputUrl: String,
        internalUrl: String,
        ownedSourceStagingFile: File?,
        ownedSourceDestination: File?,
        callback: (count: Int) -> Unit,
    ) {
        callback(0)
        timber.d("Importing M3U subscription")
        val existingPlaylistUrls = arrayOf(sourceUrl, internalUrl).distinct().toTypedArray()
        val stagedChannels = flow {
            openM3uInputOrThrow(inputUrl).use { input ->
                m3uParser.parse(input.buffered()).collect { parsed ->
                    emit(
                        StagedChannel(
                            channel = parsed.toChannel(internalUrl),
                            preservationRelationId = parsed.id.takeIf(String::isNotBlank),
                        )
                    )
                }
            }
        }
        val staging = stageChannels(
            progressBatchSize = BUFFER_M3U_CAPACITY,
            callback = callback,
            channels = stagedChannels,
        )
        try {
            val playlistStrategy = settings[PreferencesKeys.PLAYLIST_STRATEGY]
            val commitDatabase: suspend () -> Unit = {
                database.withTransaction {
                    val favOrHiddenRelationIds = when (playlistStrategy) {
                        PlaylistStrategy.ALL -> emptySet()
                        PlaylistStrategy.KEEP ->
                            channelDao
                                .getFavOrHiddenRelationIdsByPlaylistUrl(*existingPlaylistUrls)
                                .toHashSet()
                        else -> emptySet()
                    }
                    val favOrHiddenUrls = when (playlistStrategy) {
                        PlaylistStrategy.ALL -> emptySet()
                        PlaylistStrategy.KEEP ->
                            channelDao
                                .getFavOrHiddenUrlsByPlaylistUrlNotContainsRelationId(
                                    *existingPlaylistUrls
                                )
                                .toHashSet()
                        else -> emptySet()
                    }
                    if (sourceUrl != internalUrl) {
                        playlistDao.updateUrl(sourceUrl, internalUrl)
                    }
                    val playlist = playlistDao.get(internalUrl)?.copy(
                        title = title,
                        // The previous row may have been saved as EPG or another source.
                        source = DataSource.M3U,
                    ) ?: Playlist(title, internalUrl, source = DataSource.M3U)
                    deleteChannelsForImport(internalUrl, playlistStrategy)
                    playlistDao.insertOrReplace(playlist)
                    staging.forEachBatch(BUFFER_M3U_CAPACITY) { staged ->
                        val channelsToInsert = staged
                            .asSequence()
                            .filterNot { record ->
                                val relationId = record.preservationRelationId
                                playlistStrategy == PlaylistStrategy.KEEP &&
                                    (
                                        if (relationId == null) {
                                            record.channel.url in favOrHiddenUrls
                                        } else {
                                            relationId in favOrHiddenRelationIds
                                        }
                                    )
                            }
                            .map(StagedChannel::channel)
                            .toList()
                        if (channelsToInsert.isNotEmpty()) {
                            channelDao.insertOrReplaceAll(*channelsToInsert.toTypedArray())
                        }
                    }
                    channelDao.deleteOrphanedMetadata(internalUrl)
                }
            }
            if (ownedSourceStagingFile != null && ownedSourceDestination != null) {
                ownedSourceStagingFile.commitAsOwnedM3uFile(
                    destination = ownedSourceDestination,
                    commitDatabase = commitDatabase,
                )
            } else {
                commitDatabase()
            }
            replaceExtensionContributions(
                cancelPlaylistUrls = listOf(sourceUrl, internalUrl),
                schedulePlaylistUrls = listOf(internalUrl),
            )
        } finally {
            staging.close()
        }
    }

    override suspend fun xtreamOrThrow(
        title: String,
        basicUrl: String,
        username: String,
        password: String,
        type: String?,
        callback: (count: Int) -> Unit
    ): Unit = PlaylistDataMaintenanceCoordinator.withExclusive {
        val input = XtreamInput(basicUrl, username, password, type)
        val (
            liveCategories,
            vodCategories,
            serialCategories,
            allowedOutputFormats,
            serverProtocol,
            port
        ) = withContext(Dispatchers.IO) {
            xtreamParser.getXtreamOutput(input)
        }

        // we like ts but not m3u8.
        val liveContainerExtension = if ("ts" in allowedOutputFormats) "ts"
        else allowedOutputFormats.firstOrNull() ?: "ts"

        val livePlaylistUrl = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_LIVE),
            serverProtocol = serverProtocol,
            port = port
        )
        val vodPlaylistUrl = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_VOD),
            serverProtocol = serverProtocol,
            port = port
        )
        val seriesPlaylistUrl = XtreamInput.encodeToPlaylistUrl(
            input = input.copy(type = DataSource.Xtream.TYPE_SERIES),
            serverProtocol = serverProtocol,
            port = port
        )

        extensionContributionRunCoordinator.withPlaylists(
            listOf(livePlaylistUrl, vodPlaylistUrl, seriesPlaylistUrl)
        ) {
            val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
            val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
            val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES
            val requiredPlaylistUrls = buildList {
                if (requiredLives) add(livePlaylistUrl)
                if (requiredVods) add(vodPlaylistUrl)
                if (requiredSeries) add(seriesPlaylistUrl)
            }
            callback(0)
            val stagedChannels = xtreamParser.parse(input).map { current ->
                when (current) {
                    is XtreamLive -> StagedChannel(
                        channel = current.toChannel(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = livePlaylistUrl,
                            category = liveCategories
                                .find { it.categoryId == current.categoryId }
                                ?.categoryName
                                .orEmpty(),
                            containerExtension = liveContainerExtension,
                        ),
                        preservationRelationId = current.streamId?.toString(),
                    )

                    is XtreamVod -> StagedChannel(
                        channel = current.toChannel(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = vodPlaylistUrl,
                            category = vodCategories
                                .find { it.categoryId == current.categoryId }
                                ?.categoryName
                                .orEmpty(),
                        ),
                        preservationRelationId = current.streamId?.toString(),
                    )

                    // Series are persisted as channels and resolved when selected.
                    is XtreamSerial -> StagedChannel(
                        channel = current.asChannel(
                            basicUrl = basicUrl,
                            username = username,
                            password = password,
                            playlistUrl = seriesPlaylistUrl,
                            category = serialCategories
                                .find { it.categoryId == current.categoryId }
                                ?.categoryName
                                .orEmpty(),
                        ),
                        preservationRelationId = current.seriesId?.toString(),
                    )
                }
            }
            val staging = stageChannels(
                progressBatchSize = BUFFER_XTREAM_CAPACITY,
                callback = callback,
                channels = stagedChannels,
            )
            try {
                val playlistStrategy = settings[PreferencesKeys.PLAYLIST_STRATEGY]
                database.withTransaction {
                    val favOrHiddenRelationIdsByPlaylistUrl: Map<String, Set<String>> =
                        when (playlistStrategy) {
                            PlaylistStrategy.ALL -> emptyMap()
                            PlaylistStrategy.KEEP ->
                                requiredPlaylistUrls
                                    .associateWith { playlistUrl ->
                                        channelDao
                                            .getFavOrHiddenRelationIdsByPlaylistUrl(playlistUrl)
                                            .toHashSet()
                                    }
                            else -> emptyMap()
                        }
                    val requiredPlaylists = requiredPlaylistUrls.map { playlistUrl ->
                        currentXtreamPlaylist(title, playlistUrl)
                    }
                    requiredPlaylists.forEach { playlist ->
                        deleteChannelsForImport(playlist.url, playlistStrategy)
                        playlistDao.insertOrReplace(playlist)
                    }
                    staging.forEachBatch(BUFFER_XTREAM_CAPACITY) { staged ->
                        val channelsToInsert = staged
                            .asSequence()
                            .filterNot { record ->
                                playlistStrategy == PlaylistStrategy.KEEP &&
                                    isXtreamRelationPreserved(
                                        playlistUrl = record.channel.playlistUrl,
                                        relationId = record.preservationRelationId,
                                        preservedRelationIdsByPlaylistUrl =
                                            favOrHiddenRelationIdsByPlaylistUrl,
                                    )
                            }
                            .map(StagedChannel::channel)
                            .toList()
                        if (channelsToInsert.isNotEmpty()) {
                            channelDao.insertOrReplaceAll(*channelsToInsert.toTypedArray())
                        }
                    }
                    requiredPlaylists.forEach { playlist ->
                        channelDao.deleteOrphanedMetadata(playlist.url)
                    }
                }
                replaceExtensionContributions(
                    cancelPlaylistUrls = requiredPlaylistUrls,
                    schedulePlaylistUrls = requiredPlaylistUrls,
                )
            } finally {
                staging.close()
            }
        }
    }

    private suspend fun stageChannels(
        progressBatchSize: Int,
        callback: (count: Int) -> Unit,
        channels: Flow<StagedChannel>,
    ): BoundedJsonlRecordStaging<StagedChannel> = stageRecords(
        progressBatchSize = progressBatchSize,
        callback = callback,
        records = channels,
        encode = { channel ->
            CHANNEL_STAGING_JSON.encodeToString(StagedChannel.serializer(), channel)
        },
        decode = { encoded ->
            CHANNEL_STAGING_JSON.decodeFromString(StagedChannel.serializer(), encoded)
        },
    )

    private suspend fun <T> stageRecords(
        progressBatchSize: Int,
        callback: (count: Int) -> Unit,
        records: Flow<T>,
        encode: (T) -> String,
        decode: (String) -> T,
    ): BoundedJsonlRecordStaging<T> {
        val staging: BoundedJsonlRecordStaging<T> = BoundedJsonlRecordStaging.create(
            cacheDirectory = File(
                context.cacheDir,
                PLAYLIST_IMPORT_STAGING_DIRECTORY,
            ),
            limits = CHANNEL_STAGING_LIMITS,
            encode = encode,
            decode = decode,
        )
        var lastReportedCount = 0
        return try {
            withContext(Dispatchers.IO) {
                records.collect { record ->
                    val count = staging.append(record)
                    if (count % progressBatchSize == 0) {
                        callback(count)
                        lastReportedCount = count
                    }
                }
                currentCoroutineContext().ensureActive()
                staging.seal()
                if (staging.recordCount != lastReportedCount) {
                    callback(staging.recordCount)
                }
            }
            staging
        } catch (error: Throwable) {
            staging.close()
            throw error
        }
    }

    private suspend fun deleteChannelsForImport(
        playlistUrl: String,
        @PlaylistStrategy playlistStrategy: Int,
    ) {
        when (playlistStrategy) {
            PlaylistStrategy.ALL -> channelDao.deleteByPlaylistUrl(playlistUrl)
            PlaylistStrategy.KEEP ->
                channelDao.deleteByPlaylistUrlIgnoreFavOrHidden(playlistUrl)
        }
    }

    private suspend fun currentXtreamPlaylist(
        title: String,
        url: String,
    ): Playlist = playlistDao.get(url)
        ?.takeIf { playlist -> playlist.source == DataSource.Xtream }
        ?.copy(title = title)
        ?: Playlist(
            title = title,
            url = url,
            source = DataSource.Xtream,
        )

    override suspend fun insertEpgAsPlaylist(
        title: String,
        epg: String,
    ) {
        PlaylistDataMaintenanceCoordinator.withExclusive {
            playlistDao.insertOrReplace(
                Playlist(
                    title = title,
                    url = epg,
                    source = DataSource.EPG
                )
            )
        }
    }

    override suspend fun refresh(
        url: String,
        reason: PlaylistRefreshReason,
    ) {
        enqueueRefreshWork(url, reason)
    }

    override suspend fun refreshWithWorkId(
        url: String,
        reason: PlaylistRefreshReason,
    ): UUID? = enqueueRefreshWork(url, reason)

    private suspend fun enqueueRefreshWork(
        url: String,
        reason: PlaylistRefreshReason,
    ): UUID? {
        val playlist = get(url) ?: run {
            timber.w("Playlist refresh skipped because the subscription no longer exists")
            return null
        }
        if (!playlist.refreshable) {
            timber.w("Playlist refresh skipped because the subscription is not refreshable")
            return null
        }

        return when (playlist.source) {
            DataSource.M3U -> {
                SubscriptionWorker.m3u(
                    workManager = workManager,
                    title = playlist.title,
                    url = url,
                    requireExistingPlaylist = true,
                )
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
                    password = xtreamInput.password,
                    requireExistingPlaylist = true,
                )
            }

            DataSource.Emby, DataSource.Jellyfin, DataSource.Provider -> {
                ProviderRefreshWorker.enqueue(
                    workManager = workManager,
                    playlistUrl = url,
                    reason = when (reason) {
                        PlaylistRefreshReason.USER -> SubscriptionRefreshReason.Manual
                        PlaylistRefreshReason.BACKGROUND -> SubscriptionRefreshReason.Background
                    },
                )
            }

            else -> throw IllegalStateException("Refresh data source ${playlist.source} is unsupported currently.")
        }
    }

    override suspend fun backupOrThrow(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val json = Json {
            prettyPrint = false
        }
        val snapshot = PlaylistDataMaintenanceCoordinator.withExclusive {
            database.withTransaction {
                    val accounts = providerDao.getAccounts()
                        .mapNotNull(ProviderAccountBackup::fromEntity)
                    val accountIds = accounts.mapTo(mutableSetOf(), ProviderAccountBackup::id)
                    // Extension trust and overlays are not portable backup state.
                    val sourceChannelsByPlaylist = channelDao.getAllWithSourceMetadata()
                        .groupBy(Channel::playlistUrl)
                    PlaylistBackupSnapshot(
                        accounts = accounts,
                        playlists = playlistDao.getAll().map { playlist ->
                            PlaylistWithChannels(
                                playlist = playlist,
                                channels = sourceChannelsByPlaylist[playlist.url].orEmpty(),
                            )
                        },
                        playbackReferences = providerDao.getPlaybackReferences()
                            .filter { reference -> reference.accountId in accountIds }
                            .map(ProviderPlaybackReferenceBackup::fromEntity),
                    )
            }
        }
        val backupAccountByPlaylistUrl =
            snapshot.accounts.associateBy(ProviderAccountBackup::playlistUrl)
        val output = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Unable to open backup destination")
        output.bufferedWriter().use { writer ->
            snapshot.playlists.forEach { (playlist, channels) ->
                currentCoroutineContext().ensureActive()
                val isProviderPlaylist = playlist.source.isSubscriptionProvider
                if (isProviderPlaylist && playlist.url !in backupAccountByPlaylistUrl) {
                    return@forEach
                }
                val playlistToBackup = if (isProviderPlaylist) {
                    playlist.toProviderBackupCopy()
                } else {
                    playlist
                }
                val encodedPlaylist = json.encodeToString(playlistToBackup)
                val wrappedPlaylist = BackupOrRestoreContracts.wrapPlaylist(encodedPlaylist)
                writer.appendLine(wrappedPlaylist)

                channels.forEach { channel ->
                    currentCoroutineContext().ensureActive()
                    val channelToBackup = if (isProviderPlaylist) {
                        channel.toRestorableProviderBackupCopyOrNull()
                            ?: return@forEach
                    } else {
                        channel
                    }
                    val encodedChannel = json.encodeToString(channelToBackup)
                    val wrappedChannel = BackupOrRestoreContracts.wrapChannel(encodedChannel)
                    writer.appendLine(wrappedChannel)
                }
            }
            snapshot.accounts.forEach { account ->
                currentCoroutineContext().ensureActive()
                writer.appendLine(
                    BackupOrRestoreContracts.wrapProviderAccount(json.encodeToString(account))
                )
            }
            snapshot.playbackReferences.forEach { reference ->
                currentCoroutineContext().ensureActive()
                writer.appendLine(
                    BackupOrRestoreContracts.wrapPlaybackReference(json.encodeToString(reference))
                )
            }
        }
    }

    override suspend fun restoreOrThrow(uri: Uri): Unit = withContext(Dispatchers.IO) {
        val json = Json {
            ignoreUnknownKeys = true
        }
        val stagedBackup = BackupStagingFiles.create(context.cacheDir)
        try {
            val source = context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to open backup source")
            source.use { input ->
                BackupStagingFiles.copyBounded(input, stagedBackup)
            }
            val providerMetadata = readProviderRestoreMetadata(stagedBackup, json)
            PlaylistDataMaintenanceCoordinator.withExclusive {
                providerLifecycleCoordinator.withExclusiveRestore {
                    database.withTransaction {
                    val restorableProviderMetadata = providerMetadata.excludingConflictsWith(
                        existingAccounts = providerDao.getAccounts(),
                    )
                    val channels = mutableListOf<Channel>()
                    val restoredProviderPlaylistUrls = mutableSetOf<String>()
                    // An early collision must not claim an explicit ID from a later backup record.
                    var nextRemappedOrdinaryChannelId = maxOf(
                        maximumPersistedChannelId(),
                        providerMetadata.maximumOrdinaryChannelId.toLong(),
                    ).coerceAtMost(Int.MAX_VALUE.toLong()) + 1L

                    fun allocateOrdinaryChannelId(): Int {
                        check(nextRemappedOrdinaryChannelId <= Int.MAX_VALUE) {
                            "Restored ordinary channel id is outside the supported range"
                        }
                        return nextRemappedOrdinaryChannelId.toInt().also {
                            nextRemappedOrdinaryChannelId++
                        }
                    }

                    suspend fun flushChannels() {
                        if (channels.isEmpty()) return
                        val reservedIds = persistedChannelIds(
                            channels.mapTo(linkedSetOf(), Channel::id)
                        ).toMutableSet()
                        val remappedChannels = channels.map { channel ->
                            if (channel.id > 0 && reservedIds.add(channel.id)) {
                                channel
                            } else {
                                channel.copy(id = allocateOrdinaryChannelId())
                            }
                        }
                        channelDao.insertOrReplaceAll(*remappedChannels.toTypedArray())
                        channels.clear()
                    }

                    BackupStagingFiles.forEachLine(stagedBackup) { line ->
                        if (line.isBlank()) return@forEachLine
                        val encodedPlaylist = BackupOrRestoreContracts.unwrapPlaylist(line)
                        val encodedChannel = BackupOrRestoreContracts.unwrapChannel(line)
                        when {
                            encodedPlaylist != null -> {
                                val playlist = json.decodeFromString<Playlist>(encodedPlaylist)
                                when {
                                    playlist.source.isSubscriptionProvider -> {
                                        val sanitized = playlist.toProviderBackupCopy()
                                        if (
                                            sanitized.url in
                                            restorableProviderMetadata.accountsByPlaylistUrl
                                        ) {
                                            playlistDao.insertOrReplace(sanitized)
                                            restoredProviderPlaylistUrls += sanitized.url
                                        }
                                    }

                                    !playlist.url.isProviderPlaylistNamespace() ->
                                        playlistDao.insertOrReplace(playlist)
                                }
                            }

                            encodedChannel != null -> {
                                val channel = json.decodeFromString<Channel>(encodedChannel)
                                when {
                                    !channel.playlistUrl.isProviderPlaylistNamespace() -> {
                                        channels += channel
                                        if (channels.size >= BUFFER_RESTORE_CAPACITY) {
                                            flushChannels()
                                        }
                                    }

                                    else -> Unit
                                }
                            }

                            else -> Unit
                        }
                    }
                    flushChannels()

                    val providerChannels = mutableListOf<ProviderChannelRestoreEntry>()
                    val providerChannelBackupIds = mutableSetOf<Int>()
                    val providerChannelReferencesByPlaylist =
                        mutableMapOf<String, MutableSet<String>>()
                    val restoredProviderChannelIds = mutableMapOf<Int, Int>()
                    val providerChannelCountByPlaylist = mutableMapOf<String, Int>()
                    var restoredProviderChannelCount = 0

                    suspend fun flushProviderChannels() {
                        if (providerChannels.isEmpty()) return
                        val insertedIds = channelDao.insertOrReplaceAllAndReturnIds(
                            *providerChannels
                                .map { entry -> entry.channel.copy(id = 0) }
                                .toTypedArray()
                        )
                        check(insertedIds.size == providerChannels.size) {
                            "Room returned an unexpected provider channel id count"
                        }
                        providerChannels.zip(insertedIds).forEach { (entry, insertedId) ->
                            check(insertedId in 1..Int.MAX_VALUE.toLong()) {
                                "Restored provider channel id is outside the supported range"
                            }
                            check(
                                restoredProviderChannelIds.put(
                                    entry.backupId,
                                    insertedId.toInt(),
                                ) == null
                            ) {
                                "Backup contains duplicate provider channel identifiers"
                            }
                        }
                        providerChannels.clear()
                    }

                    BackupStagingFiles.forEachLine(stagedBackup) { line ->
                        val encodedChannel = BackupOrRestoreContracts.unwrapChannel(line)
                            ?: return@forEachLine
                        val channel = json.decodeFromString<Channel>(encodedChannel)
                        if (channel.playlistUrl !in restoredProviderPlaylistUrls) {
                            return@forEachLine
                        }
                        val sanitizedChannel =
                            channel.toRestorableProviderBackupCopyOrNull()
                                ?: return@forEachLine
                        require(providerChannelBackupIds.add(sanitizedChannel.id)) {
                            "Backup contains duplicate provider channel identifiers"
                        }
                        require(
                            providerChannelReferencesByPlaylist
                                .getOrPut(sanitizedChannel.playlistUrl) { mutableSetOf() }
                                .add(requireNotNull(sanitizedChannel.relationId))
                        ) {
                            "Backup contains duplicate provider channel references"
                        }
                        val playlistChannelCount = providerChannelCountByPlaylist
                            .getOrDefault(sanitizedChannel.playlistUrl, 0) + 1
                        require(
                            playlistChannelCount <=
                                MAX_RESTORED_PROVIDER_CHANNELS_PER_PLAYLIST
                        ) {
                            "Backup contains too many channels for one provider playlist"
                        }
                        providerChannelCountByPlaylist[sanitizedChannel.playlistUrl] =
                            playlistChannelCount
                        restoredProviderChannelCount++
                        require(
                            restoredProviderChannelCount <=
                                MAX_RESTORED_PROVIDER_CHANNELS_TOTAL
                        ) {
                            "Backup contains too many provider channels"
                        }
                        providerChannels += ProviderChannelRestoreEntry(
                            backupId = sanitizedChannel.id,
                            channel = sanitizedChannel,
                        )
                        if (providerChannels.size >= BUFFER_RESTORE_CAPACITY) {
                            flushProviderChannels()
                        }
                    }
                    flushProviderChannels()
                    val validProviderAccounts =
                        restorableProviderMetadata.accountsByPlaylistUrl.values
                        .filter { account ->
                            account.playlistUrl in restoredProviderPlaylistUrls
                        }
                    validProviderAccounts.forEach { account ->
                        providerDao.restoreReauthenticationRequiredAccount(account)
                    }
                    val accountById = validProviderAccounts.associateBy { account -> account.id }
                    val restoredPlaybackReferenceBackupIds = mutableSetOf<Int>()
                    BackupStagingFiles.forEachLine(stagedBackup) { line ->
                        val encodedReference =
                            BackupOrRestoreContracts.unwrapPlaybackReference(line)
                                ?: return@forEachLine
                        val reference = json.decodeFromString<ProviderPlaybackReferenceBackup>(
                            encodedReference
                        )
                        val account = accountById[reference.accountId] ?: return@forEachLine
                        val restoredChannelId =
                            restoredProviderChannelIds[reference.channelId]
                                ?: return@forEachLine
                        val restoredReference = reference.copy(channelId = restoredChannelId)
                        val channelPlaylistUrl =
                            channelDao.get(restoredChannelId)?.playlistUrl
                        val entity = restoredReference
                            .takeIf {
                                it.isValidForRestore(
                                    account = account,
                                    channelPlaylistUrl = channelPlaylistUrl,
                                    restoredProviderPlaylistUrls =
                                        restoredProviderPlaylistUrls,
                                )
                            }
                            ?.toEntityOrNull()
                            ?: return@forEachLine
                        require(
                            restoredPlaybackReferenceBackupIds.add(reference.channelId)
                        ) {
                            "Backup contains duplicate provider playback references"
                        }
                        providerDao.insertOrReplace(entity)
                    }
                    }
                }
            }
        } finally {
            BackupStagingFiles.release(stagedBackup)
        }
    }

    private fun maximumPersistedChannelId(): Long =
        database.openHelper.writableDatabase
            .query("SELECT MAX(id) FROM streams")
            .use { cursor ->
                check(cursor.moveToFirst()) {
                    "Unable to read the maximum persisted channel id"
                }
                if (cursor.isNull(0)) 0L else cursor.getLong(0)
            }

    private fun persistedChannelIds(candidateIds: Collection<Int>): Set<Int> {
        val positiveIds = candidateIds.filter { id -> id > 0 }
        if (positiveIds.isEmpty()) return emptySet()
        val placeholders = List(positiveIds.size) { "?" }.joinToString()
        val arguments = Array<Any?>(positiveIds.size) { index -> positiveIds[index] }
        return database.openHelper.writableDatabase
            .query(
                "SELECT id FROM streams WHERE id IN ($placeholders)",
                arguments,
            )
            .use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getInt(0))
                    }
                }
            }
    }

    private suspend fun readProviderRestoreMetadata(
        stagedBackup: File,
        json: Json,
    ): ProviderRestoreMetadata {
        val accountsById = linkedMapOf<String, ProviderAccount>()
        val accountsByPlaylistUrl = linkedMapOf<String, ProviderAccount>()
        val providerPlaylistUrls = linkedSetOf<String>()
        var maximumOrdinaryChannelId = 0
        BackupStagingFiles.forEachLine(stagedBackup) { line ->
            if (line.isBlank()) return@forEachLine
            BackupOrRestoreContracts.unwrapProviderAccount(line)?.let { encodedAccount ->
                val account = json.decodeFromString<ProviderAccountBackup>(encodedAccount)
                    .toEntityOrNull()
                    ?: return@let
                require(accountsById.putIfAbsent(account.id, account) == null) {
                    "Backup contains duplicate provider account identifiers"
                }
                require(
                    accountsByPlaylistUrl.putIfAbsent(account.playlistUrl, account) == null
                ) {
                    "Backup contains duplicate provider account playlists"
                }
                require(accountsById.size <= MAX_RESTORED_PROVIDER_ACCOUNTS) {
                    "Backup contains too many provider accounts"
                }
                return@forEachLine
            }
            BackupOrRestoreContracts.unwrapChannel(line)?.let { encodedChannel ->
                val channel = json.decodeFromString<Channel>(encodedChannel)
                if (!channel.playlistUrl.isProviderPlaylistNamespace()) {
                    maximumOrdinaryChannelId = maxOf(
                        maximumOrdinaryChannelId,
                        channel.id,
                    )
                }
                return@forEachLine
            }
            BackupOrRestoreContracts.unwrapPlaylist(line)?.let { encodedPlaylist ->
                val playlist = json.decodeFromString<Playlist>(encodedPlaylist)
                if (
                    playlist.source.isSubscriptionProvider &&
                    playlist.url.isProviderPlaylistNamespace()
                ) {
                    providerPlaylistUrls += playlist.url
                    require(providerPlaylistUrls.size <= MAX_RESTORED_PROVIDER_ACCOUNTS) {
                        "Backup contains too many provider playlists"
                    }
                }
            }
        }
        return ProviderRestoreMetadata(
            accountsByPlaylistUrl = accountsByPlaylistUrl.filterKeys(providerPlaylistUrls::contains),
            maximumOrdinaryChannelId = maximumOrdinaryChannelId,
        )
    }

    override suspend fun pinOrUnpinCategory(url: String, category: String) {
        PlaylistDataMaintenanceCoordinator.withExclusive {
            playlistDao.updatePinnedCategories(url) { prev ->
                if (category in prev) prev - category
                else prev + category
            }
        }
    }

    override suspend fun hideOrUnhideCategory(url: String, category: String) {
        PlaylistDataMaintenanceCoordinator.withExclusive {
            playlistDao.hideOrUnhideCategory(url, category)
        }
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
        val canonicalM3uUrl = if (
            url.toUri().scheme.equals(
                ContentResolver.SCHEME_CONTENT,
                ignoreCase = true,
            )
        ) {
            url.resolveM3uSourceLocation().internalUrl
        } else {
            url
        }
        val candidateUrls = listOf(url, canonicalM3uUrl).distinct()
        candidateUrls.forEach { candidateUrl ->
            // One release-cycle compatibility: older requests used the raw URL as a tag.
            workManager.cancelAllWorkByTag(candidateUrl).await()
            workManager.cancelAllWorkByTag(playlistWorkTag(candidateUrl)).await()
            workManager.cancelUniqueWork(m3uSubscriptionWorkName(candidateUrl)).await()
            workManager.cancelUniqueWork(epgSubscriptionWorkName(candidateUrl)).await()
            workManager.cancelAllWorkByTag(xtreamPlaylistWorkTag(candidateUrl)).await()
        }
        return PlaylistDataMaintenanceCoordinator.withExclusive {
            val target = playlistDao.get(url)
                ?: canonicalM3uUrl
                    .takeIf { candidate -> candidate != url }
                    ?.let { candidate -> playlistDao.get(candidate) }
                    ?.takeIf { candidate -> candidate.source == DataSource.M3U }
            if (target?.source?.isSubscriptionProvider == true) {
                val removed = providerLifecycleCoordinator.withOperation {
                    subscriptionProviderRepository.removeAccount(target.url)
                    target
                }
                replaceExtensionContributions(
                    cancelPlaylistUrls = candidateUrls + target.url,
                    schedulePlaylistUrls = emptyList(),
                )
                removed
            } else {
                extensionContributionRunCoordinator.withPlaylists(
                    candidateUrls + listOfNotNull(target?.url)
                ) {
                    val current = database.withTransaction {
                        target?.also {
                            channelDao.deleteByPlaylistUrl(it.url)
                            playlistDao.delete(it)
                        }
                    }
                    replaceExtensionContributions(
                        cancelPlaylistUrls = candidateUrls,
                        schedulePlaylistUrls = emptyList(),
                    )
                    current?.deleteOwnedM3uFile()
                    current
                }
            }
        }
    }

    override suspend fun onUpdatePlaylistTitle(url: String, title: String) {
        val normalizedTitle = normalizePlaylistTitle(title) ?: return
        PlaylistDataMaintenanceCoordinator.withExclusive {
            playlistDao.updateTitle(url, normalizedTitle)
        }
    }

    override suspend fun onUpdatePlaylistUserAgent(url: String, userAgent: String?) {
        val normalizedUserAgent = userAgent
            ?.normalizePlaylistInputForSubmission(PlaylistInputKind.USER_AGENT)
            ?.takeIf(String::isNotEmpty)
        PlaylistDataMaintenanceCoordinator.withExclusive {
            playlistDao.updateUserAgent(url, normalizedUserAgent)
        }
    }

    override fun observeAllCounts(): Flow<Map<Playlist, Int>> = playlistDao.observeAllCounts()
            .map { it.toMap() }
            .catch { emit(emptyMap()) }

    override suspend fun readEpisodesOrThrow(series: Channel): List<XtreamEpisodeInfo> {
        val playlist = checkNotNull(get(series.playlistUrl)) { "playlist is not exist" }
        val seriesInfo = xtreamParser.getSeriesInfoOrThrow(
            input = XtreamInput.decodeFromPlaylistUrl(playlist.url),
            seriesId = Url(series.url).rawSegments.last().toInt()
        )
        // fixme: do not flatmap
        return seriesInfo.episodes.flatMap { it.value }.map { it.toXtreamEpisodeInfo() }
    }

    override suspend fun deleteEpgPlaylistAndProgrammes(
        epgUrl: String,
    ) {
        // One release-cycle compatibility: older EPG requests used the raw URL as a tag.
        workManager.cancelAllWorkByTag(epgUrl).await()
        workManager.cancelUniqueWork(epgSubscriptionWorkName(epgUrl)).await()
        PlaylistDataMaintenanceCoordinator.withExclusive {
            EpgDataMaintenanceCoordinator.withExclusive(epgUrl) {
                database.withTransaction {
                    playlistDao.deleteByUrl(epgUrl)
                    programmeDao.deleteAllByEpgUrl(epgUrl)
                    playlistDao.removeEpgUrlForAllPlaylists(epgUrl)
                }
            }
        }
    }

    private suspend fun scheduleExtensionContributions(playlistUrl: String) {
        try {
            extensionContributionScheduler.enqueue(playlistUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            timber.w(
                "Extension contribution scheduling failed (%s)",
                error.javaClass.simpleName,
            )
        }
    }

    private suspend fun cancelExtensionContributions(playlistUrl: String) {
        try {
            extensionContributionScheduler.cancel(playlistUrl)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            timber.w(
                "Extension contribution cancellation failed (%s)",
                error.javaClass.simpleName,
            )
        }
    }

    private suspend fun replaceExtensionContributions(
        cancelPlaylistUrls: Collection<String>,
        schedulePlaylistUrls: Collection<String>,
    ) = withContext(NonCancellable) {
        cancelPlaylistUrls
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .forEach { playlistUrl ->
                cancelExtensionContributions(playlistUrl)
            }
        schedulePlaylistUrls
            .asSequence()
            .filter(String::isNotBlank)
            .distinct()
            .forEach { playlistUrl ->
                scheduleExtensionContributions(playlistUrl)
            }
    }

    override suspend fun onUpdateEpgPlaylist(useCase: PlaylistRepository.EpgPlaylistUseCase) {
        val lockedEpgUrl = when (useCase) {
            is PlaylistRepository.EpgPlaylistUseCase.Check -> useCase.epgUrl
            is PlaylistRepository.EpgPlaylistUseCase.Upgrade -> useCase.epgUrl
        }
        PlaylistDataMaintenanceCoordinator.withExclusive {
            EpgDataMaintenanceCoordinator.withExclusive(lockedEpgUrl) epgLock@ {
                when (useCase) {
                    is PlaylistRepository.EpgPlaylistUseCase.Check -> {
                        if (
                            useCase.action &&
                            playlistDao.get(useCase.epgUrl)?.source != DataSource.EPG
                        ) {
                            return@epgLock
                        }
                        playlistDao.updateEpgUrls(useCase.playlistUrl) { epgUrls ->
                            when {
                                useCase.action && useCase.epgUrl !in epgUrls ->
                                    epgUrls + useCase.epgUrl
                                useCase.action -> epgUrls
                                else -> epgUrls - useCase.epgUrl
                            }
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
        }
    }

    override suspend fun onUpdatePlaylistAutoRefreshProgrammes(playlistUrl: String) {
        PlaylistDataMaintenanceCoordinator.withExclusive {
            val playlist = playlistDao.get(playlistUrl) ?: return@withExclusive
            playlistDao.updatePlaylistAutoRefreshProgrammes(
                playlistUrl,
                !playlist.autoRefreshProgrammes
            )
        }
    }

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

    private suspend fun String.resolveM3uSourceLocation(): M3uSourceLocation {
        if (!isSupportedAndroidUrl()) return M3uSourceLocation(this)
        val uri = this.toUri()
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return M3uSourceLocation(uri.toString())
        }
        return withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, LOCAL_M3U_DIRECTORY)
            val filename = hashedWorkTag(
                namespace = "local-m3u-file",
                value = uri.toString(),
            ).substringAfter(':') + LOCAL_M3U_EXTENSION
            val destinationFile = File(directory, filename)
            M3uSourceLocation(
                internalUrl = destinationFile.toUri().toString(),
                destinationFile = destinationFile,
            )
        }
    }

    private suspend fun Uri.copyToOwnedM3uStagingFile(destination: File): File {
        val directory = checkNotNull(destination.parentFile)
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create the local playlist directory"
        }
        val temporary = File.createTempFile(
            "${destination.name}.",
            LOCAL_M3U_TEMPORARY_SUFFIX,
            directory,
        )
        return try {
            withContext(Dispatchers.IO) {
                val input = context.contentResolver
                    .openInputStream(this@copyToOwnedM3uStagingFile)
                    ?: throw IOException("Unable to open the selected playlist")
                input.use { source ->
                    FileOutputStream(temporary).use { output ->
                        val buffer = ByteArray(LOCAL_M3U_COPY_BUFFER_BYTES)
                        var totalBytes = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = source.read(buffer)
                            if (count < 0) break
                            if (count == 0) continue
                            if (totalBytes > MAX_LOCAL_M3U_BYTES - count) {
                                throw IOException("The selected playlist is too large")
                            }
                            output.write(buffer, 0, count)
                            totalBytes += count
                        }
                        output.fd.sync()
                    }
                }
            }
            temporary
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                temporary.deleteOrTruncate()
            }
            throw error
        }
    }

    private suspend fun File.commitAsOwnedM3uFile(
        destination: File,
        commitDatabase: suspend () -> Unit,
    ) {
        val directory = checkNotNull(destination.parentFile)
        val rollback = File.createTempFile(
            "${destination.name}.",
            LOCAL_M3U_ROLLBACK_SUFFIX,
            directory,
        ).also { placeholder ->
            check(placeholder.delete()) {
                "Unable to prepare local playlist rollback"
            }
        }
        var destinationInstalled = false
        var databaseCommitted = false
        var primaryFailure: Throwable? = null
        try {
            withContext(Dispatchers.IO) {
                if (destination.exists()) {
                    destination.moveReplacing(rollback)
                }
                this@commitAsOwnedM3uFile.moveReplacing(destination)
                destinationInstalled = true
            }
            commitDatabase()
            databaseCommitted = true
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupFailure = withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    if (!databaseCommitted) {
                        if (destinationInstalled) {
                            destination.deleteOrTruncate()
                        }
                        if (rollback.exists()) {
                            rollback.moveReplacing(destination)
                        }
                    }
                    this@commitAsOwnedM3uFile.deleteOrTruncate()
                    rollback.deleteOrTruncate()
                }.exceptionOrNull()
            }
            if (cleanupFailure != null) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure)
                } else {
                    throw cleanupFailure
                }
            }
        }
    }

    private fun File.moveReplacing(destination: File) {
        try {
            Files.move(
                toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun File.deleteOrTruncate() {
        if (exists() && !delete()) {
            runCatching { writeBytes(byteArrayOf()) }
        }
    }

    private suspend fun Playlist.deleteOwnedM3uFile() {
        if (source != DataSource.M3U) return
        val uri = url.toUri()
        if (uri.scheme != ContentResolver.SCHEME_FILE) return
        withContext(Dispatchers.IO) {
            val directory = File(context.filesDir, LOCAL_M3U_DIRECTORY).canonicalFile
            val candidate = runCatching { File(checkNotNull(uri.path)).canonicalFile }
                .getOrNull()
                ?: return@withContext
            if (candidate.parentFile == directory) {
                candidate.delete()
            }
        }
    }

    private fun openM3uInputOrThrow(url: String): InputStream = when {
        url.isSupportedNetworkUrl() -> openNetworkInput(url)
        url.isSupportedAndroidUrl() -> openAndroidInput(url)
        else -> throw IOException("Unsupported playlist location")
    }

    private fun openNetworkInput(url: String): InputStream {
        val request = Request.Builder()
            .url(url)
            .build()
        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            throw IOException("Unable to download playlist (HTTP ${response.code})")
        }
        return response.body.byteStream()
    }

    private fun openAndroidInput(url: String): InputStream {
        val uri = url.toUri()
        return context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open playlist")
    }

    private companion object {
        const val LOCAL_M3U_DIRECTORY = "playlists"
        const val PLAYLIST_IMPORT_STAGING_DIRECTORY = "playlist-import-staging"
        const val LOCAL_M3U_EXTENSION = ".m3u"
        const val LOCAL_M3U_TEMPORARY_SUFFIX = ".tmp"
        const val LOCAL_M3U_ROLLBACK_SUFFIX = ".rollback"
        const val LOCAL_M3U_COPY_BUFFER_BYTES = 32 * 1024
        const val MAX_LOCAL_M3U_BYTES = 256L * 1024L * 1024L
    }
}

internal fun isXtreamRelationPreserved(
    playlistUrl: String,
    relationId: String?,
    preservedRelationIdsByPlaylistUrl: Map<String, Set<String>>,
): Boolean = relationId != null &&
    relationId in preservedRelationIdsByPlaylistUrl[playlistUrl].orEmpty()

private fun normalizePlaylistTitle(title: String): String? =
    title
        .normalizePlaylistInputForSubmission(PlaylistInputKind.TITLE)
        .takeIf(String::isNotEmpty)
