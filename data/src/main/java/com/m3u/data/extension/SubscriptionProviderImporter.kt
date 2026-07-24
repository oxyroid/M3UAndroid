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
import com.m3u.data.database.model.ExtensionChannelMetadataOverlay
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.Programme
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.repository.extension.ExtensionEpgRefreshContribution
import com.m3u.data.repository.extension.ExtensionMetadataRefreshContribution
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.ProviderAccountReference
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Singleton
internal class SubscriptionProviderImporter @Inject constructor(
    private val database: M3UDatabase,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val providerDao: ProviderDao,
    private val programmeDao: ProgrammeDao,
    private val credentialVault: CredentialVault,
) {
    private val extensionMetadataMutex = Mutex()
    private var extensionMetadataGeneration = 0L
    private val extensionMetadataInvalidatedAt = mutableMapOf<ExtensionId, Long>()
    private val extensionEpgMutex = Mutex()
    private var extensionEpgGeneration = 0L
    private val extensionEpgInvalidatedAt = mutableMapOf<ExtensionId, Long>()

    suspend fun importSubscription(
        title: String,
        source: DataSource,
        account: ProviderAccount,
        accessToken: String,
        refresh: SubscriptionContentRefreshResult,
    ): Int {
        validateProviderSnapshot(account, refresh)
        return database.withTransaction {
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
            importChannels(account, refresh)
        }
    }

    suspend fun refresh(
        account: ProviderAccount,
        refresh: SubscriptionContentRefreshResult,
    ): Int {
        validateProviderSnapshot(account, refresh)
        return database.withTransaction {
            require(providerDao.getAccount(account.id) == account) {
                "Provider account changed before its snapshot was imported"
            }
            importChannels(account, refresh)
        }
    }

    private suspend fun importChannels(
        account: ProviderAccount,
        refresh: SubscriptionContentRefreshResult,
    ): Int {
        val existingChannels = repairDuplicateProviderChannels(
            account = account,
            channels = channelDao.getByPlaylistUrl(account.playlistUrl),
        )
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
                    // External artwork stays untrusted until it has a brokered host-owned path.
                    cover = descriptor.logoUrl.takeIf { account.ownerPackageName == null },
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
                )
            )
        }

        existingChannels
            .filter { channel ->
                channel.relationId !in refreshedRemoteIds && !channel.favourite && !channel.hidden
            }
            .forEach { channel -> channelDao.delete(channel) }
        channelDao.deleteOrphanedMetadata(account.playlistUrl)
        return refresh.channels.size
    }

    private suspend fun repairDuplicateProviderChannels(
        account: ProviderAccount,
        channels: List<Channel>,
    ): List<Channel> {
        val duplicateGroups = channels
            .asSequence()
            .filter { channel -> !channel.relationId.isNullOrBlank() }
            .groupBy { channel -> requireNotNull(channel.relationId) }
            .values
            .filter { group -> group.size > 1 }
        if (duplicateGroups.isEmpty()) return channels

        val referencesByChannelId =
            providerDao.getPlaybackReferencesByPlaylistUrl(account.playlistUrl)
                .associateBy(ChannelPlaybackReference::channelId)
        duplicateGroups
            .sortedBy { group -> group.minOf(Channel::id) }
            .forEach { group ->
                val ordered = group.sortedBy(Channel::id)
                val canonical = ordered.first()
                val mergedCanonical = canonical.copy(
                    favourite = ordered.any(Channel::favourite),
                    hidden = ordered.any(Channel::hidden),
                    seen = ordered.maxOf(Channel::seen),
                )
                if (mergedCanonical != canonical) {
                    channelDao.insertOrReplace(mergedCanonical)
                }

                ordered
                    .asSequence()
                    .mapNotNull { channel -> referencesByChannelId[channel.id] }
                    .firstOrNull { reference -> reference.isUsableFor(account) }
                    ?.let { reference ->
                        providerDao.insertOrReplace(
                            reference.copy(channelId = canonical.id)
                        )
                    }

                ordered.drop(1).forEach { duplicate ->
                    channelDao.delete(duplicate)
                }
            }
        return channelDao.getByPlaylistUrl(account.playlistUrl)
    }

    private fun ChannelPlaybackReference.isUsableFor(
        account: ProviderAccount,
    ): Boolean =
        accountId == account.id &&
            providerId == account.providerId &&
            runCatching {
                PlaybackReference(
                    providerId = ExtensionId(providerId),
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    sourceType = sourceType,
                )
            }.isSuccess

    internal fun validateProviderSnapshot(
        account: ProviderAccount,
        refresh: SubscriptionContentRefreshResult,
    ) = validateProviderSnapshot(
        accountId = account.id,
        providerId = account.providerId,
        providerKind = account.providerKind,
        baseUrl = account.baseUrl,
        serverId = account.serverId,
        userId = account.userId,
        allowRemoteArtwork = account.ownerPackageName == null,
        refresh = refresh,
    )

    internal fun validateProviderSnapshot(
        account: ProviderAccountReference,
        allowRemoteArtwork: Boolean,
        refresh: SubscriptionContentRefreshResult,
    ) = validateProviderSnapshot(
        accountId = account.accountId,
        providerId = account.providerId.value,
        providerKind = account.providerKind.value,
        baseUrl = account.baseUrl,
        serverId = account.serverId,
        userId = account.userId,
        allowRemoteArtwork = allowRemoteArtwork,
        refresh = refresh,
    )

    private fun validateProviderSnapshot(
        accountId: String,
        providerId: String,
        providerKind: String,
        baseUrl: String,
        serverId: String,
        userId: String,
        allowRemoteArtwork: Boolean,
        refresh: SubscriptionContentRefreshResult,
    ) {
        require(accountId.isNotBlank() && accountId.length <= MAX_ID_LENGTH)
        require(providerId.isNotBlank() && providerId.length <= MAX_ID_LENGTH)
        require(providerKind.length <= ProviderKind.MAX_LENGTH)
        require(serverId.isNotBlank() && serverId.length <= MAX_ID_LENGTH)
        require(userId.isNotBlank() && userId.length <= MAX_ID_LENGTH)
        require(baseUrl.toHttpUrlOrNull() != null)
        require(refresh.source.remoteId == serverId)
        require(refresh.source.providerKind.value == providerKind)
        require(refresh.channels.size <= MAX_CHANNELS_PER_REFRESH)
        require(refresh.channels.map { descriptor -> descriptor.remoteId }.distinct().size ==
            refresh.channels.size)
        val approvedOrigin = baseUrl.toHttpUrlOrNull()?.origin
        refresh.channels.forEach { descriptor ->
            require(descriptor.remoteId.isNotBlank() && descriptor.remoteId.length <= MAX_ID_LENGTH)
            require(descriptor.title.isSafeExtensionText(MAX_TITLE_LENGTH))
            require(
                descriptor.category.isSafeExtensionText(
                    maximumLength = MAX_TITLE_LENGTH,
                    allowBlank = true,
                )
            )
            if (allowRemoteArtwork) {
                require(descriptor.logoUrl?.isApprovedUrl(
                    approvedOrigin = approvedOrigin,
                    restrictOrigin = false,
                    requireStableValue = true,
                ) ?: true)
            }
            val reference = descriptor.playbackReference
            require(reference.providerId.value == providerId)
            require(reference.itemId.isNotBlank() && reference.itemId.length <= MAX_ID_LENGTH)
            require(reference.sourceType.isNotBlank() && reference.sourceType.length <= MAX_ID_LENGTH)
            require(reference.mediaSourceId?.length?.let { it <= MAX_ID_LENGTH } ?: true)
        }
    }

    private fun String.isSafeExtensionText(
        maximumLength: Int,
        allowBlank: Boolean = false,
    ): Boolean =
        (allowBlank || isNotBlank()) &&
            length <= maximumLength &&
            none { character ->
                character.isISOControl() ||
                    character.code in 0x202A..0x202E ||
                    character.code in 0x2066..0x2069 ||
                    character.code == 0x200E ||
                    character.code == 0x200F
            }

    private fun String.isApprovedUrl(
        approvedOrigin: String?,
        restrictOrigin: Boolean,
        requireStableValue: Boolean = false,
    ): Boolean {
        if (length > MAX_URL_LENGTH) return false
        val url = toHttpUrlOrNull() ?: return false
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return false
        if (requireStableValue && (url.query != null || url.fragment != null)) return false
        return !restrictOrigin || url.origin == approvedOrigin
    }

    private val HttpUrl.origin: String
        get() = "$scheme://$host:$port"

    suspend fun metadataSnapshots(playlistUrl: String): List<ChannelMetadataSnapshot> =
        channelDao.getMetadataBases(playlistUrl)
            .mapNotNull { base ->
                base.channelReference.takeIf(String::isNotBlank)?.let { stableReference ->
                    ChannelMetadataSnapshot(
                        stableReference = stableReference,
                        title = base.title,
                        category = base.category,
                    )
                }
            }

    suspend fun captureExtensionMetadataRefreshGeneration(): ExtensionMetadataRefreshGeneration =
        extensionMetadataMutex.withLock {
            ExtensionMetadataRefreshGeneration(extensionMetadataGeneration)
        }

    suspend fun applyMetadataEnrichment(
        playlistUrl: String,
        refreshes: List<ExtensionMetadataRefreshContribution>,
    ): Int = extensionMetadataMutex.withLock {
        applyMetadataEnrichmentLocked(playlistUrl, refreshes)
    }

    suspend fun applyMetadataEnrichment(
        playlistUrl: String,
        refreshes: List<ExtensionMetadataRefreshContribution>,
        refreshGeneration: ExtensionMetadataRefreshGeneration,
    ): Int = extensionMetadataMutex.withLock {
        val currentRefreshes = refreshes.filter { refresh ->
            extensionMetadataInvalidatedAt[refresh.extensionId]
                ?.let { invalidatedAt -> invalidatedAt <= refreshGeneration.value }
                ?: true
        }
        applyMetadataEnrichmentLocked(playlistUrl, currentRefreshes)
    }

    private suspend fun applyMetadataEnrichmentLocked(
        playlistUrl: String,
        refreshes: List<ExtensionMetadataRefreshContribution>,
    ): Int = database.withTransaction {
        channelDao.deleteOrphanedMetadata(playlistUrl)
        if (refreshes.isEmpty()) return@withTransaction 0

        val knownReferences = channelDao.getMetadataBases(playlistUrl)
            .mapTo(mutableSetOf()) { base -> base.channelReference }
        val affectedReferences = linkedSetOf<String>()
        var acceptedCount = 0
        refreshes
            .groupBy(ExtensionMetadataRefreshContribution::extensionId)
            .forEach { (extensionId, extensionRefreshes) ->
                val previous = channelDao.getMetadataOverlays(
                    playlistUrl = playlistUrl,
                    extensionId = extensionId.value,
                )
                affectedReferences += previous.map(
                    ExtensionChannelMetadataOverlay::channelReference
                )
                channelDao.deleteMetadataOverlays(
                    playlistUrl = playlistUrl,
                    extensionId = extensionId.value,
                )

                val accepted = extensionRefreshes
                    .asSequence()
                    .flatMap { refresh -> refresh.patches.asSequence() }
                    .filter { patch ->
                        patch.stableReference in knownReferences &&
                            (patch.title != null || patch.category != null)
                    }
                    .distinctBy { patch -> patch.stableReference }
                    .map { patch ->
                        ExtensionChannelMetadataOverlay(
                            playlistUrl = playlistUrl,
                            channelReference = patch.stableReference,
                            extensionId = extensionId.value,
                            title = patch.title,
                            category = patch.category,
                        )
                    }
                    .toList()
                if (accepted.isNotEmpty()) {
                    channelDao.upsertMetadataOverlays(*accepted.toTypedArray())
                    affectedReferences += accepted.map(
                        ExtensionChannelMetadataOverlay::channelReference
                    )
                }
                acceptedCount += accepted.size
            }

        affectedReferences.forEach { reference ->
            channelDao.recomputeEffectiveMetadata(playlistUrl, reference)
        }
        acceptedCount
    }

    suspend fun clearExtensionMetadata(extensionId: ExtensionId): Int =
        extensionMetadataMutex.withLock {
            extensionMetadataGeneration += 1
            extensionMetadataInvalidatedAt[extensionId] = extensionMetadataGeneration
            clearExtensionMetadataLocked(extensionId)
        }

    private suspend fun clearExtensionMetadataLocked(extensionId: ExtensionId): Int =
        database.withTransaction {
            val previous = channelDao.getMetadataOverlays(extensionId.value)
            if (previous.isEmpty()) return@withTransaction 0
            val removedCount = channelDao.deleteMetadataOverlays(extensionId.value)
            previous
                .asSequence()
                .map { overlay -> overlay.playlistUrl to overlay.channelReference }
                .distinct()
                .forEach { (playlistUrl, channelReference) ->
                    channelDao.recomputeEffectiveMetadata(playlistUrl, channelReference)
                }
            removedCount
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
                        icon = null,
                        categories = item.categories,
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
        const val MAX_CHANNELS_PER_REFRESH = 50_000
        const val MAX_ID_LENGTH = 512
        const val MAX_TITLE_LENGTH = 1_024
        const val MAX_URL_LENGTH = 8_192
        fun extensionEpgSource(extensionId: ExtensionId, encodedPlaylist: String): String =
            "$EXTENSION_EPG_SCHEME${extensionId.value}/$encodedPlaylist"
    }
}

internal data class ExtensionEpgRefreshGeneration(
    val value: Long,
)

internal data class ExtensionMetadataRefreshGeneration(
    val value: Long,
)
