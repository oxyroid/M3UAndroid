package com.m3u.data.extension

import androidx.room.withTransaction
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.dao.ProviderDao
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.extension.security.CredentialVault
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import javax.inject.Inject

internal class SubscriptionProviderImporter @Inject constructor(
    private val database: M3UDatabase,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val providerDao: ProviderDao,
    private val credentialVault: CredentialVault,
) {
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
}
