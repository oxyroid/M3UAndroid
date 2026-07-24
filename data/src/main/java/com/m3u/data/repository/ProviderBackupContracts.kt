package com.m3u.data.repository

import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

/**
 * Stable, intentionally limited representation of a provider account in an app backup.
 *
 * Credentials live outside this contract. Keep this type separate from the Room entity so adding
 * a persisted field cannot accidentally make it part of an exported backup.
 */
@Serializable
internal data class ProviderAccountBackup(
    val id: String,
    val providerId: String,
    val providerKind: String,
    val baseUrl: String,
    val serverId: String,
    val serverName: String,
    val serverVersion: String,
    val userId: String,
    val username: String,
    val playlistUrl: String,
    val ownerPackageName: String? = null,
    val ownerServiceName: String? = null,
    val ownerCertificateSha256: String? = null,
) {
    fun toEntityOrNull(): ProviderAccount? {
        if (
            id.isBlank() ||
            id.length > MAX_BACKUP_ACCOUNT_ID_LENGTH ||
            '/' in id ||
            serverId.isBlank() ||
            serverId.length > MAX_BACKUP_REMOTE_ID_LENGTH ||
            userId.isBlank() ||
            userId.length > MAX_BACKUP_REMOTE_ID_LENGTH ||
            serverName.isBlank() ||
            serverName.length > MAX_BACKUP_LABEL_LENGTH ||
            serverVersion.length > MAX_BACKUP_LABEL_LENGTH ||
            username.length > MAX_BACKUP_LABEL_LENGTH ||
            providerId.length > MAX_BACKUP_CONTRACT_ID_LENGTH ||
            providerKind.length > MAX_BACKUP_CONTRACT_ID_LENGTH ||
            baseUrl.length > MAX_BACKUP_URL_LENGTH ||
            playlistUrl.length > MAX_BACKUP_URL_LENGTH ||
            runCatching { ExtensionId(providerId) }.isFailure ||
            runCatching { ProviderKind(providerKind) }.isFailure ||
            !hasValidOwnerMetadata()
        ) {
            return null
        }
        val safeBaseUrl = sanitizeProviderBaseUrl(baseUrl) ?: return null
        val safePlaylistUrl = validateProviderPlaylistUrl(id, playlistUrl) ?: return null
        return ProviderAccount(
            id = id,
            providerId = providerId,
            providerKind = providerKind,
            baseUrl = safeBaseUrl,
            serverId = serverId,
            serverName = serverName,
            serverVersion = serverVersion,
            userId = userId,
            username = username,
            playlistUrl = safePlaylistUrl,
            requiresReauthentication = true,
            ownerPackageName = ownerPackageName,
            ownerServiceName = ownerServiceName,
            ownerCertificateSha256 = ownerCertificateSha256,
        )
    }

    private fun hasValidOwnerMetadata(): Boolean {
        val ownerValues = listOf(
            ownerPackageName,
            ownerServiceName,
            ownerCertificateSha256,
        )
        if (ownerValues.all { it == null }) return true
        return ownerValues.all { value ->
            value != null && value.isNotBlank() && value.length <= MAX_BACKUP_OWNER_FIELD_LENGTH
        }
    }

    companion object {
        fun fromEntity(account: ProviderAccount): ProviderAccountBackup? {
            val safeBaseUrl = sanitizeProviderBaseUrl(account.baseUrl) ?: return null
            val safePlaylistUrl = validateProviderPlaylistUrl(
                accountId = account.id,
                value = account.playlistUrl,
            ) ?: return null
            return ProviderAccountBackup(
                id = account.id,
                providerId = account.providerId,
                providerKind = account.providerKind,
                baseUrl = safeBaseUrl,
                serverId = account.serverId,
                serverName = account.serverName,
                serverVersion = account.serverVersion,
                userId = account.userId,
                username = account.username,
                playlistUrl = safePlaylistUrl,
                ownerPackageName = account.ownerPackageName,
                ownerServiceName = account.ownerServiceName,
                ownerCertificateSha256 = account.ownerCertificateSha256,
            ).takeIf { backup -> backup.toEntityOrNull() != null }
        }
    }
}

/**
 * Stable playback lookup data. A resolved fallback URL can contain a short-lived access token, so
 * it is deliberately omitted and must be resolved again after restore.
 */
@Serializable
internal data class ProviderPlaybackReferenceBackup(
    val channelId: Int,
    val accountId: String,
    val providerId: String,
    val itemId: String,
    val mediaSourceId: String? = null,
    val sourceType: String,
) {
    fun toEntityOrNull(): ChannelPlaybackReference? {
        if (
            channelId <= 0 ||
            accountId.isBlank() ||
            accountId.length > MAX_BACKUP_ACCOUNT_ID_LENGTH ||
            providerId.length > MAX_BACKUP_CONTRACT_ID_LENGTH ||
            runCatching { ExtensionId(providerId) }.isFailure ||
            itemId.isBlank() ||
            itemId.encodeToByteArray().size > MAX_BACKUP_REMOTE_ID_UTF8_BYTES ||
            (
                mediaSourceId != null &&
                    (
                        mediaSourceId.isBlank() ||
                            mediaSourceId.encodeToByteArray().size >
                            MAX_BACKUP_REMOTE_ID_UTF8_BYTES
                    )
                ) ||
            sourceType.isBlank() ||
            sourceType.encodeToByteArray().size > MAX_BACKUP_SOURCE_TYPE_UTF8_BYTES
        ) {
            return null
        }
        return ChannelPlaybackReference(
            channelId = channelId,
            accountId = accountId,
            providerId = providerId,
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            sourceType = sourceType,
        )
    }

    companion object {
        fun fromEntity(reference: ChannelPlaybackReference): ProviderPlaybackReferenceBackup =
            ProviderPlaybackReferenceBackup(
                channelId = reference.channelId,
                accountId = reference.accountId,
                providerId = reference.providerId,
                itemId = reference.itemId,
                mediaSourceId = reference.mediaSourceId,
                sourceType = reference.sourceType,
            )
    }
}

internal val DataSource.isSubscriptionProvider: Boolean
    get() = this == DataSource.Emby || this == DataSource.Jellyfin || this == DataSource.Provider

internal fun Playlist.toProviderBackupCopy(): Playlist = copy(
    userAgent = null,
    epgUrls = emptyList(),
)

internal fun Channel.toProviderBackupCopy(): Channel = copy(
    url = Channel.URL_DYNAMIC,
    cover = null,
    licenseType = null,
    licenseKey = null,
)

/**
 * Restored provider rows are treated like a fresh provider refresh. This keeps a damaged backup
 * from bypassing the limits applied to live extension output.
 */
internal fun Channel.toRestorableProviderBackupCopyOrNull(): Channel? {
    val stableReference = relationId ?: return null
    if (
        id <= 0 ||
        stableReference.isBlank() ||
        stableReference.encodeToByteArray().size > MAX_BACKUP_REMOTE_ID_UTF8_BYTES ||
        !title.isSafeProviderBackupText(MAX_BACKUP_LABEL_LENGTH) ||
        !category.isSafeProviderBackupText(MAX_BACKUP_LABEL_LENGTH, allowBlank = true)
    ) {
        return null
    }
    return toProviderBackupCopy()
}

/**
 * Provider restore is additive. Existing subscriptions remain authoritative so a backup from
 * another device cannot replace their identity, playlist, owner, or working credential.
 */
internal fun selectRestorableProviderAccounts(
    incoming: Iterable<ProviderAccount>,
    existing: Iterable<ProviderAccount>,
): List<ProviderAccount> {
    val reservedIds = existing.mapTo(mutableSetOf(), ProviderAccount::id)
    val reservedPlaylistUrls = existing.mapTo(mutableSetOf(), ProviderAccount::playlistUrl)
    val reservedRemoteIdentities = existing.mapTo(mutableSetOf()) { account ->
        ProviderRemoteIdentity(
            providerId = account.providerId,
            serverId = account.serverId,
            userId = account.userId,
        )
    }
    return buildList {
        incoming.forEach { account ->
            val remoteIdentity = ProviderRemoteIdentity(
                providerId = account.providerId,
                serverId = account.serverId,
                userId = account.userId,
            )
            if (
                account.id in reservedIds ||
                account.playlistUrl in reservedPlaylistUrls ||
                remoteIdentity in reservedRemoteIdentities
            ) {
                return@forEach
            }
            add(account)
            reservedIds += account.id
            reservedPlaylistUrls += account.playlistUrl
            reservedRemoteIdentities += remoteIdentity
        }
    }
}

internal fun String.isProviderPlaylistNamespace(): Boolean {
    return providerAccountIdOrNull() != null
}

internal fun String.providerAccountIdOrNull(): String? {
    if (length > MAX_BACKUP_URL_LENGTH) return null
    val parsed = runCatching { URI(this) }.getOrNull() ?: return null
    val pathSegments = parsed.rawPath
        ?.split('/')
        ?.filter(String::isNotEmpty)
        .orEmpty()
    return pathSegments.firstOrNull()?.takeIf {
        parsed.scheme == "m3u-provider" &&
        parsed.host == "account" &&
        pathSegments.size == 2 &&
        pathSegments.first().isNotBlank() &&
        pathSegments.last() == "live" &&
        parsed.rawUserInfo == null &&
        parsed.rawQuery == null &&
        parsed.rawFragment == null
    }
}

internal fun ProviderPlaybackReferenceBackup.isValidForRestore(
    account: ProviderAccount,
    channelPlaylistUrl: String?,
    restoredProviderPlaylistUrls: Set<String>,
): Boolean =
    accountId == account.id &&
        providerId == account.providerId &&
        account.playlistUrl in restoredProviderPlaylistUrls &&
        channelPlaylistUrl == account.playlistUrl

internal fun sanitizeProviderBaseUrl(value: String): String? {
    val parsed = value.toHttpUrlOrNull() ?: return null
    if (parsed.scheme != "http" && parsed.scheme != "https") return null
    return parsed.newBuilder()
        .username("")
        .password("")
        .query(null)
        .fragment(null)
        .build()
        .toString()
}

private fun validateProviderPlaylistUrl(accountId: String, value: String): String? {
    val parsed = runCatching { URI(value) }.getOrNull() ?: return null
    return value.takeIf {
        value.isProviderPlaylistNamespace() &&
            parsed.host == "account" &&
            parsed.rawPath == "/$accountId/live" &&
            parsed.rawPath == parsed.path
    }
}

private data class ProviderRemoteIdentity(
    val providerId: String,
    val serverId: String,
    val userId: String,
)

private fun String.isSafeProviderBackupText(
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

private const val MAX_BACKUP_ACCOUNT_ID_LENGTH = 128
private const val MAX_BACKUP_CONTRACT_ID_LENGTH = 128
private const val MAX_BACKUP_REMOTE_ID_LENGTH = 512
private const val MAX_BACKUP_REMOTE_ID_UTF8_BYTES = 512
private const val MAX_BACKUP_SOURCE_TYPE_UTF8_BYTES = 128
private const val MAX_BACKUP_LABEL_LENGTH = 1_024
private const val MAX_BACKUP_URL_LENGTH = 2_048
private const val MAX_BACKUP_OWNER_FIELD_LENGTH = 1_024
