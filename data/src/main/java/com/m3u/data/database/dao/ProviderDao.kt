package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderAccountSummaryRow
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.ProviderPlaybackSessionEntity
import kotlinx.coroutines.flow.Flow

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

    @Query(
        """
        SELECT provider_accounts.*, playlists.title AS playlist_title
        FROM provider_accounts
        INNER JOIN playlists ON playlists.url = provider_accounts.playlist_url
        ORDER BY playlists.title, provider_accounts.id
        """
    )
    fun observeAccountSummaries(): Flow<List<ProviderAccountSummaryRow>>

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

    @Query("DELETE FROM provider_playback_sessions WHERE account_id = :accountId")
    suspend fun deletePlaybackSessions(accountId: String)

    @Transaction
    suspend fun restoreReauthenticationRequiredAccount(account: ProviderAccount) {
        deleteCredential(account.id)
        deletePlaybackSessions(account.id)
        insertOrReplace(account.copy(requiresReauthentication = true))
    }

    @Query("UPDATE provider_accounts SET requires_reauthentication = :required WHERE id = :accountId")
    suspend fun setRequiresReauthentication(accountId: String, required: Boolean)

    @Transaction
    suspend fun invalidateCredential(accountId: String) {
        deleteCredential(accountId)
        setRequiresReauthentication(accountId, true)
    }

    @Query(
        """
        SELECT * FROM provider_accounts
        WHERE provider_id = :extensionId
        AND owner_package_name = :packageName
        AND owner_service_name = :serviceName
        AND owner_certificate_sha256 = :certificateSha256
        """
    )
    suspend fun getAccountsOwnedBy(
        extensionId: String,
        packageName: String,
        serviceName: String,
        certificateSha256: String,
    ): List<ProviderAccount>

    @Query(
        """
        DELETE FROM provider_credentials
        WHERE account_id IN (
            SELECT id FROM provider_accounts
            WHERE provider_id = :extensionId
            AND owner_package_name = :packageName
            AND owner_service_name = :serviceName
            AND owner_certificate_sha256 = :certificateSha256
        )
        """
    )
    suspend fun deleteCredentialsOwnedBy(
        extensionId: String,
        packageName: String,
        serviceName: String,
        certificateSha256: String,
    ): Int

    @Query(
        """
        DELETE FROM provider_playback_sessions
        WHERE account_id IN (
            SELECT id FROM provider_accounts
            WHERE provider_id = :extensionId
            AND owner_package_name = :packageName
            AND owner_service_name = :serviceName
            AND owner_certificate_sha256 = :certificateSha256
        )
        """
    )
    suspend fun deletePlaybackSessionsOwnedBy(
        extensionId: String,
        packageName: String,
        serviceName: String,
        certificateSha256: String,
    ): Int

    @Query(
        """
        UPDATE provider_accounts SET requires_reauthentication = 1
        WHERE provider_id = :extensionId
        AND owner_package_name = :packageName
        AND owner_service_name = :serviceName
        AND owner_certificate_sha256 = :certificateSha256
        """
    )
    suspend fun requireReauthenticationForOwner(
        extensionId: String,
        packageName: String,
        serviceName: String,
        certificateSha256: String,
    ): Int

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
