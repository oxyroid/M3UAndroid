package com.m3u.data.extension

import androidx.room.withTransaction
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProgrammeDao
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.Programme
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.repository.extension.ExtensionEpgRefreshContribution
import com.m3u.data.repository.extension.ExtensionMetadataContribution
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
internal class SubscriptionProviderImporter @Inject constructor(
    private val database: M3UDatabase,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val providerDao: ProviderDao,
    private val programmeDao: ProgrammeDao,
    private val credentialVault: CredentialVault,
) {
    private val extensionEpgMutex = Mutex()
    private var extensionEpgGeneration = 0L
    private val extensionEpgInvalidatedAt = mutableMapOf<ExtensionId, Long>()

    suspend fun import(
        title: String,
        source: DataSource,
        account: ProviderAccount,
        accessToken: String,
        refresh: SubscriptionContentRefreshResult,
    ): Int = database.withTransaction {
        val existingPlaylist = playlistDao.get(account.playlistUrl)
        if (existingPlaylist == null) {
            playlistDao.insertOrReplace(
                Playlist(
                    title = title,
                    url = account.playlistUrl,
                    source = source,
                )
            )
        } else {
            playlistDao.updateProviderPlaylist(
                url = account.playlistUrl,
                title = title,
                source = source,
            )
        }
        providerDao.insertOrReplace(account)
        val existingCredential = providerDao.getCredential(account.id)
        providerDao.insertOrReplace(
            credentialVault.encrypt(
                accountId = account.id,
                secret = accessToken,
                credentialHandle = existingCredential?.credentialHandle,
            )
        )

        val existingChannels = channelDao.getByPlaylistUrl(account.playlistUrl)
        val existingByRemoteId = existingChannels
            .mapNotNull { channel -> channel.relationId?.let { remoteId -> remoteId to channel } }
            .toMap()
        val refreshedRemoteIds = mutableSetOf<String>()
        refresh.channels.forEach { descriptor ->
            refreshedRemoteIds += descriptor.remoteId
            val existing = existingByRemoteId[descriptor.remoteId]
            val channelId = channelDao.insertOrReplace(
                Channel(
                    url = Channel.URL_DYNAMIC,
                    category = descriptor.category,
                    title = descriptor.title,
                    cover = descriptor.logoUrl,
                    playlistUrl = account.playlistUrl,
                    id = existing?.id ?: 0,
                    favourite = existing?.favourite ?: false,
                    hidden = existing?.hidden ?: false,
                    seen = existing?.seen ?: 0L,
                    relationId = descriptor.remoteId,
                )
            ).toInt()
            val reference = descriptor.playbackReference
            providerDao.insertOrReplace(
                ChannelPlaybackReference(
                    channelId = channelId,
                    accountId = account.id,
                    providerId = reference.providerId.value,
                    itemId = reference.itemId,
                    mediaSourceId = reference.mediaSourceId,
                    sourceType = reference.sourceType,
                    fallbackDirectUrl = reference.fallbackDirectUrl,
                )
            )
        }

        existingChannels
            .filter { channel ->
                channel.relationId !in refreshedRemoteIds && !channel.favourite && !channel.hidden
            }
            .forEach { channel -> channelDao.delete(channel) }
        refresh.channels.size
    }

    suspend fun metadataSnapshots(playlistUrl: String): List<ChannelMetadataSnapshot> = channelDao
        .getByPlaylistUrl(playlistUrl)
        .mapNotNull { channel ->
            channel.relationId?.let { stableReference ->
                ChannelMetadataSnapshot(
                    stableReference = stableReference,
                    title = channel.title,
                    category = channel.category,
                )
            }
        }

    suspend fun applyMetadataEnrichment(
        playlistUrl: String,
        contributions: List<ExtensionMetadataContribution>,
    ): Int = database.withTransaction {
        if (contributions.isEmpty()) return@withTransaction 0
        val channelsByReference = channelDao.getByPlaylistUrl(playlistUrl)
            .mapNotNull { channel ->
                channel.relationId?.let { stableReference -> stableReference to channel }
            }
            .toMap()
        contributions.count { contribution ->
            val patch = contribution.patch
            val channel = channelsByReference[patch.stableReference]
                ?: return@count false
            if (patch.title == null && patch.category == null) return@count false
            channelDao.applyMetadataEnrichment(channel.id, patch.title, patch.category)
            true
        }
    }

    suspend fun captureExtensionEpgRefreshGeneration(): ExtensionEpgRefreshGeneration =
        extensionEpgMutex.withLock {
            ExtensionEpgRefreshGeneration(extensionEpgGeneration)
        }

    suspend fun replaceExtensionEpg(
        playlistUrl: String,
        refreshes: List<ExtensionEpgRefreshContribution>,
    ): Int = extensionEpgMutex.withLock {
        replaceExtensionEpgLocked(playlistUrl, refreshes)
    }

    suspend fun replaceExtensionEpg(
        playlistUrl: String,
        refreshes: List<ExtensionEpgRefreshContribution>,
        refreshGeneration: ExtensionEpgRefreshGeneration,
    ): Int = extensionEpgMutex.withLock {
        val currentRefreshes = refreshes.filter { contribution ->
            extensionEpgInvalidatedAt[contribution.extensionId]
                ?.let { invalidatedAt -> invalidatedAt <= refreshGeneration.value }
                ?: true
        }
        replaceExtensionEpgLocked(playlistUrl, currentRefreshes)
    }

    private suspend fun replaceExtensionEpgLocked(
        playlistUrl: String,
        refreshes: List<ExtensionEpgRefreshContribution>,
    ): Int = database.withTransaction {
        val playlist = playlistDao.get(playlistUrl) ?: return@withTransaction 0
        if (refreshes.isEmpty()) return@withTransaction 0

        val knownReferences = channelDao.getByPlaylistUrl(playlistUrl)
            .mapNotNullTo(mutableSetOf(), Channel::relationId)
        val encodedPlaylist = URLEncoder.encode(
            playlistUrl,
            StandardCharsets.UTF_8.name(),
        )
        val refreshesByExtension = refreshes
            .groupBy(ExtensionEpgRefreshContribution::extensionId)
            .mapValues { (_, batches) ->
                batches.flatMap(ExtensionEpgRefreshContribution::programmes)
            }
        val refreshedSources = refreshesByExtension.keys.associateWith { extensionId ->
            extensionEpgSource(extensionId, encodedPlaylist)
        }
        refreshedSources.values.forEach { source -> programmeDao.cleanByEpgUrl(source) }

        var acceptedCount = 0
        val activeSources = linkedSetOf<String>()
        refreshesByExtension.forEach { (extensionId, programmes) ->
            val source = refreshedSources.getValue(extensionId)
            val accepted = programmes
                .asSequence()
                .filter { programme -> programme.channelReference in knownReferences }
                .distinctBy { programme ->
                    with(programme) {
                        listOf(channelReference, startEpochMillis, endEpochMillis, title)
                    }
                }
                .toList()
            if (accepted.isNotEmpty()) activeSources += source
            accepted.forEach { item ->
                programmeDao.insertOrReplace(
                    Programme(
                        channelId = item.channelReference,
                        epgUrl = source,
                        start = item.startEpochMillis,
                        end = item.endEpochMillis,
                        title = item.title,
                        description = item.description.orEmpty(),
                        icon = item.metadata["icon"],
                        categories = item.metadata["categories"]
                            ?.split(',')
                            ?.map(String::trim)
                            ?.filter(String::isNotBlank)
                            .orEmpty(),
                    )
                )
            }
            acceptedCount += accepted.size
        }
        playlistDao.replaceEpgUrls(
            playlistUrl,
            playlist.epgUrls.filterNot(refreshedSources.values::contains) + activeSources,
        )
        acceptedCount
    }

    suspend fun clearExtensionEpg(extensionId: ExtensionId): Int = extensionEpgMutex.withLock {
        extensionEpgGeneration += 1
        extensionEpgInvalidatedAt[extensionId] = extensionEpgGeneration
        database.withTransaction {
            val prefix = "$EXTENSION_EPG_SCHEME${extensionId.value}/"
            var removedSources = 0
            playlistDao.getAll().forEach { playlist ->
                val ownedSources = playlist.epgUrls.filter { source -> source.startsWith(prefix) }
                if (ownedSources.isNotEmpty()) {
                    ownedSources.forEach { source -> programmeDao.cleanByEpgUrl(source) }
                    playlistDao.replaceEpgUrls(
                        playlist.url,
                        playlist.epgUrls - ownedSources.toSet(),
                    )
                    removedSources += ownedSources.size
                }
            }
            removedSources
        }
    }

    private companion object {
        const val EXTENSION_EPG_SCHEME = "m3u-extension-epg://"

        fun extensionEpgSource(extensionId: ExtensionId, encodedPlaylist: String): String =
            "$EXTENSION_EPG_SCHEME${extensionId.value}/$encodedPlaylist"
    }
}

internal data class ExtensionEpgRefreshGeneration(
    val value: Long,
)
