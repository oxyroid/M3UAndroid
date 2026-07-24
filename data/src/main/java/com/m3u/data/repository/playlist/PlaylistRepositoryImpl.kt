package com.m3u.data.repository.playlist

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.m3u.core.foundation.architecture.preferences.PlaylistStrategy
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.get
import com.m3u.core.foundation.util.basic.startsWithAny
import com.m3u.core.foundation.util.copyToFile
import com.m3u.core.foundation.util.readFileName
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
import com.m3u.data.parser.m3u.M3UData
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
import com.m3u.data.repository.ProviderAccountBackup
import com.m3u.data.repository.ProviderPlaybackReferenceBackup
import com.m3u.data.repository.createCoroutineCache
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
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

private const val BUFFER_M3U_CAPACITY = 500
private const val BUFFER_XTREAM_CAPACITY = 100
private const val BUFFER_RESTORE_CAPACITY = 400
private const val MAX_RESTORED_PROVIDER_ACCOUNTS = 256
private const val MAX_RESTORED_PROVIDER_CHANNELS_PER_PLAYLIST = 50_000
private const val MAX_RESTORED_PROVIDER_CHANNELS_TOTAL = 200_000

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
    ) {
        val location = url.resolveM3uSourceLocation()
        extensionContributionRunCoordinator.withPlaylists(listOf(url, location.internalUrl)) {
            cancelExtensionContributions(url)
            if (url != location.internalUrl) {
                cancelExtensionContributions(location.internalUrl)
            }
            val internalUrl = location.destinationFile?.let { destination ->
                val copied = withContext(Dispatchers.IO) {
                    url.toUri().copyToFile(context.contentResolver, destination)
                }
                if (copied) location.internalUrl else url
            } ?: location.internalUrl
            if (url != internalUrl) {
                playlistDao.updateUrl(url, internalUrl)
            }
            importM3uLocked(
                title = title,
                url = url,
                internalUrl = internalUrl,
                callback = callback,
            )
        }
    }

    private suspend fun importM3uLocked(
        title: String,
        url: String,
        internalUrl: String,
        callback: (count: Int) -> Unit,
    ) {
        var currentCount = 0
        callback(currentCount)
        timber.d("m3uOrThrow: url=$url, internalUrl=$internalUrl")
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

        channelFlow {
            when {
                url.isSupportedNetworkUrl() -> openNetworkInput(internalUrl)
                url.isSupportedAndroidUrl() -> openAndroidInput(internalUrl)
                else -> null
            }?.use { input ->
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
        channelDao.deleteOrphanedMetadata(internalUrl)
        scheduleExtensionContributions(internalUrl)
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
        val livePlaylist = currentXtreamPlaylist(title, livePlaylistUrl)
        val vodPlaylist = currentXtreamPlaylist(title, vodPlaylistUrl)
        val seriesPlaylist = currentXtreamPlaylist(title, seriesPlaylistUrl)
        val requiredLives = type == null || type == DataSource.Xtream.TYPE_LIVE
        val requiredVods = type == null || type == DataSource.Xtream.TYPE_VOD
        val requiredSeries = type == null || type == DataSource.Xtream.TYPE_SERIES
        buildList {
            if (requiredLives) add(livePlaylist.url)
            if (requiredVods) add(vodPlaylist.url)
            if (requiredSeries) add(seriesPlaylist.url)
        }.forEach { playlistUrl ->
            cancelExtensionContributions(playlistUrl)
        }
        val favOrHiddenRelationIds = channelDao.getFavOrHiddenRelationIdsByPlaylistUrl(
            livePlaylist.url,
            vodPlaylist.url,
            seriesPlaylist.url
        )

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
        if (requiredLives) channelDao.deleteOrphanedMetadata(livePlaylist.url)
        if (requiredVods) channelDao.deleteOrphanedMetadata(vodPlaylist.url)
        if (requiredSeries) channelDao.deleteOrphanedMetadata(seriesPlaylist.url)
        buildList {
            if (requiredLives) add(livePlaylist.url)
            if (requiredVods) add(vodPlaylist.url)
            if (requiredSeries) add(seriesPlaylist.url)
        }.forEach { playlistUrl ->
            scheduleExtensionContributions(playlistUrl)
        }
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

    override suspend fun refresh(
        url: String,
        reason: PlaylistRefreshReason,
    ) {
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
        val snapshot = database.withTransaction {
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
        cancelExtensionContributions(url)
        return if (playlist?.source?.isSubscriptionProvider == true) {
            providerLifecycleCoordinator.withOperation {
                subscriptionProviderRepository.removeAccount(url)
                playlist
            }
        } else {
            channelDao.deleteByPlaylistUrl(url)
            playlist?.also {
                playlistDao.delete(it)
            }
        }
    }

    override suspend fun onUpdatePlaylistTitle(url: String, title: String) = playlistDao.updateTitle(url, title)

    override suspend fun onUpdatePlaylistUserAgent(url: String, userAgent: String?) = playlistDao.updateUserAgent(url, userAgent)

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

    override suspend fun deleteEpgPlaylistAndProgrammes(epgUrl: String) {
        playlistDao.deleteByUrl(epgUrl)
        programmeDao.deleteAllByEpgUrl(epgUrl)
        playlistDao.removeEpgUrlForAllPlaylists(epgUrl)
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
            val contentResolver = context.contentResolver
            val filename = uri.readFileName(contentResolver) ?: filenameWithTimezone
            val destinationFile = File(context.filesDir, filename)
            M3uSourceLocation(
                internalUrl = Uri.decode(destinationFile.toUri().toString()),
                destinationFile = destinationFile,
            )
        }
    }


    private fun openNetworkInput(url: String): InputStream? {
        val request = Request.Builder()
            .url(url)
            .build()
        val response = okHttpClient.newCall(request).execute()
        return response.body.byteStream()
    }

    private fun openAndroidInput(url: String): InputStream? {
        val uri = url.toUri()
        return context.contentResolver.openInputStream(uri)
    }
}
