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

    @Query("SELECT * FROM provider_accounts WHERE provider_id = :providerId ORDER BY id")
    suspend fun getAccountsByProviderId(providerId: String): List<ProviderAccount>

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
        deletePlaybackSessions(accountId)
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

    @Query(
        """
        UPDATE provider_accounts SET
            owner_package_name = :newPackageName,
            owner_service_name = :newServiceName,
            owner_certificate_sha256 = :newCertificateSha256,
            requires_reauthentication = 1
        WHERE provider_id = :extensionId
        AND owner_package_name = :oldPackageName
        AND owner_service_name = :oldServiceName
        AND owner_certificate_sha256 = :oldCertificateSha256
        """
    )
    suspend fun transferAccountOwner(
        extensionId: String,
        oldPackageName: String,
        oldServiceName: String,
        oldCertificateSha256: String,
        newPackageName: String,
        newServiceName: String,
        newCertificateSha256: String,
    ): Int

    @Query("SELECT * FROM provider_playback_sessions ORDER BY created_at_epoch_millis")
    suspend fun getPlaybackSessions(): List<ProviderPlaybackSessionEntity>

    @Query("SELECT COUNT(*) FROM provider_playback_sessions")
    suspend fun countPlaybackSessions(): Int

    @Query(
        "SELECT COUNT(*) FROM provider_playback_sessions WHERE account_id = :accountId"
    )
    suspend fun countPlaybackSessions(accountId: String): Int

    @Query(
        """
        SELECT * FROM provider_playback_sessions
        ORDER BY created_at_epoch_millis, id
        LIMIT :limit
        """
    )
    suspend fun getPlaybackSessionPage(limit: Int): List<ProviderPlaybackSessionEntity>

    @Query(
        """
        SELECT * FROM provider_playback_sessions
        WHERE (
            created_at_epoch_millis > :afterCreatedAtEpochMillis
            OR (
                created_at_epoch_millis = :afterCreatedAtEpochMillis
                AND id > :afterSessionId
            )
        )
        ORDER BY created_at_epoch_millis, id
        LIMIT :limit
        """
    )
    suspend fun getPlaybackSessionPageAfter(
        afterCreatedAtEpochMillis: Long,
        afterSessionId: String,
        limit: Int,
    ): List<ProviderPlaybackSessionEntity>

    @Query(
        """
        DELETE FROM provider_playback_sessions
        WHERE NOT EXISTS (
            SELECT 1
            FROM provider_accounts
            WHERE provider_accounts.id = provider_playback_sessions.account_id
            AND provider_accounts.provider_id = provider_playback_sessions.provider_id
        )
        OR NULLIF(TRIM(id), '') IS NULL
        OR LENGTH(CAST(id AS BLOB)) > 512
        OR created_at_epoch_millis < 0
        OR LENGTH(CAST(provider_id AS BLOB)) NOT BETWEEN 1 AND 128
        OR provider_id GLOB '*[^a-z0-9._-]*'
        OR SUBSTR(provider_id, 1, 1) NOT GLOB '[a-z0-9]'
        OR SUBSTR(provider_id, -1, 1) NOT GLOB '[a-z0-9]'
        OR provider_id GLOB '*[._-][._-]*'
        OR NULLIF(TRIM(item_id), '') IS NULL
        OR LENGTH(CAST(item_id AS BLOB)) > 512
        OR (
            media_source_id IS NOT NULL
            AND (
                NULLIF(TRIM(media_source_id), '') IS NULL
                OR LENGTH(CAST(media_source_id AS BLOB)) > 512
            )
        )
        OR NULLIF(TRIM(source_type), '') IS NULL
        OR LENGTH(CAST(source_type AS BLOB)) > 128
        OR (
            NULLIF(TRIM(play_session_id), '') IS NULL
            AND NULLIF(TRIM(live_stream_id), '') IS NULL
        )
        OR (
            play_session_id IS NOT NULL
            AND (
                NULLIF(TRIM(play_session_id), '') IS NULL
                OR LENGTH(CAST(play_session_id AS BLOB)) > 512
            )
        )
        OR (
            live_stream_id IS NOT NULL
            AND (
                NULLIF(TRIM(live_stream_id), '') IS NULL
                OR LENGTH(CAST(live_stream_id AS BLOB)) > 512
            )
        )
        """
    )
    suspend fun deleteInvalidPlaybackSessions(): Int

    @Transaction
    suspend fun getValidPlaybackSessionPage(
        afterCreatedAtEpochMillis: Long?,
        afterSessionId: String?,
        limit: Int,
    ): List<ProviderPlaybackSessionEntity> {
        deleteInvalidPlaybackSessions()
        return if (afterCreatedAtEpochMillis == null || afterSessionId == null) {
            getPlaybackSessionPage(limit)
        } else {
            getPlaybackSessionPageAfter(
                afterCreatedAtEpochMillis = afterCreatedAtEpochMillis,
                afterSessionId = afterSessionId,
                limit = limit,
            )
        }
    }

    @Query("SELECT * FROM provider_playback_sessions WHERE id = :sessionId")
    suspend fun getPlaybackSession(sessionId: String): ProviderPlaybackSessionEntity?

    @Query("DELETE FROM provider_playback_sessions WHERE id = :sessionId")
    suspend fun deletePlaybackSession(sessionId: String)

    @Query("SELECT * FROM channel_playback_references WHERE channel_id = :channelId")
    suspend fun getPlaybackReference(channelId: Int): ChannelPlaybackReference?

    @Query("SELECT * FROM channel_playback_references ORDER BY channel_id")
    suspend fun getPlaybackReferences(): List<ChannelPlaybackReference>

    @Query(
        """
        SELECT channel_playback_references.*
        FROM channel_playback_references
        INNER JOIN streams
        ON streams.id = channel_playback_references.channel_id
        WHERE streams.playlist_url = :playlistUrl
        ORDER BY channel_playback_references.channel_id
        """
    )
    suspend fun getPlaybackReferencesByPlaylistUrl(
        playlistUrl: String,
    ): List<ChannelPlaybackReference>

    @Query("DELETE FROM provider_accounts WHERE playlist_url = :playlistUrl")
    suspend fun deleteAccountByPlaylistUrl(playlistUrl: String)

    @Query("DELETE FROM streams WHERE playlist_url = :playlistUrl")
    suspend fun deleteChannelsByPlaylistUrl(playlistUrl: String)

    @Query("DELETE FROM playlists WHERE url = :playlistUrl")
    suspend fun deletePlaylistByUrl(playlistUrl: String)

    @Transaction
    suspend fun deleteProviderSubscription(playlistUrl: String) {
        deleteAccountByPlaylistUrl(playlistUrl)
        deleteChannelsByPlaylistUrl(playlistUrl)
        deletePlaylistByUrl(playlistUrl)
    }
}
