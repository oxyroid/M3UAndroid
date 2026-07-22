package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.ProviderPlaybackSessionEntity

@Dao
interface ProviderDao {
    @Upsert
    suspend fun insertOrReplace(account: ProviderAccount)

    @Upsert
    suspend fun insertOrReplace(credential: ProviderCredentialEntity)

    @Upsert
    suspend fun insertOrReplace(reference: ChannelPlaybackReference)

    @Upsert
    suspend fun insertOrReplace(session: ProviderPlaybackSessionEntity)

    @Query("SELECT * FROM provider_accounts WHERE id = :accountId")
    suspend fun getAccount(accountId: String): ProviderAccount?

    @Query("SELECT * FROM provider_accounts ORDER BY id")
    suspend fun getAccounts(): List<ProviderAccount>

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

    @Query("SELECT * FROM provider_credentials WHERE credential_handle = :credentialHandle")
    suspend fun getCredentialByHandle(credentialHandle: String): ProviderCredentialEntity?

    @Query("DELETE FROM provider_credentials WHERE account_id = :accountId")
    suspend fun deleteCredential(accountId: String)

    @Query("UPDATE provider_accounts SET requires_reauthentication = :required WHERE id = :accountId")
    suspend fun setRequiresReauthentication(accountId: String, required: Boolean)

    @Query("SELECT * FROM provider_playback_sessions ORDER BY created_at_epoch_millis")
    suspend fun getPlaybackSessions(): List<ProviderPlaybackSessionEntity>

    @Query("DELETE FROM provider_playback_sessions WHERE id = :sessionId")
    suspend fun deletePlaybackSession(sessionId: String)

    @Query("SELECT * FROM channel_playback_references WHERE channel_id = :channelId")
    suspend fun getPlaybackReference(channelId: Int): ChannelPlaybackReference?

    @Query("SELECT * FROM channel_playback_references ORDER BY channel_id")
    suspend fun getPlaybackReferences(): List<ChannelPlaybackReference>

    @Query("DELETE FROM provider_accounts WHERE playlist_url = :playlistUrl")
    suspend fun deleteAccountByPlaylistUrl(playlistUrl: String)
}
