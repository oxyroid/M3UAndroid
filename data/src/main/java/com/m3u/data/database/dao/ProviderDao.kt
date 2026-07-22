package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity

@Dao
interface ProviderDao {
    @Upsert
    suspend fun insertOrReplace(account: ProviderAccount)

    @Upsert
    suspend fun insertOrReplace(credential: ProviderCredentialEntity)

    @Upsert
    suspend fun insertOrReplace(reference: ChannelPlaybackReference)

    @Query("SELECT * FROM provider_accounts WHERE id = :accountId")
    suspend fun getAccount(accountId: String): ProviderAccount?

    @Query("SELECT * FROM provider_accounts WHERE playlist_url = :playlistUrl")
    suspend fun getAccountByPlaylistUrl(playlistUrl: String): ProviderAccount?

    @Query(
        """
        SELECT * FROM provider_accounts
        WHERE provider_id = :providerId
        AND server_id = :serverId
        AND user_id = :userId
        """
    )
    suspend fun getAccountByRemoteIdentity(
        providerId: String,
        serverId: String,
        userId: String,
    ): ProviderAccount?

    @Query("SELECT * FROM provider_credentials WHERE account_id = :accountId")
    suspend fun getCredential(accountId: String): ProviderCredentialEntity?

    @Query("SELECT * FROM channel_playback_references WHERE channel_id = :channelId")
    suspend fun getPlaybackReference(channelId: Int): ChannelPlaybackReference?

    @Query("DELETE FROM provider_accounts WHERE playlist_url = :playlistUrl")
    suspend fun deleteAccountByPlaylistUrl(playlistUrl: String)
}
