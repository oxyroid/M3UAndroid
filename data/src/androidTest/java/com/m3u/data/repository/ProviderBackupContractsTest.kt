package com.m3u.data.repository

import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBackupContractsTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun accountBackupRemovesUrlCredentialsQueryAndFragment() {
        val backup = requireNotNull(
            ProviderAccountBackup.fromEntity(
                account(baseUrl = "https://user:password@example.com/emby?api_key=secret#part")
            )
        )

        assertEquals("https://example.com/emby", backup.baseUrl)
        val encoded = json.encodeToString(backup)
        assertFalse(encoded.contains("password"))
        assertFalse(encoded.contains("secret"))
    }

    @Test
    fun restoredAccountAlwaysRequiresAuthentication() {
        val encodedOldEntity = """
            {
              "id":"account",
              "providerId":"builtin.media-server",
              "providerKind":"emby",
              "baseUrl":"https://example.com/emby?api_key=old-token",
              "serverId":"server",
              "serverName":"Media",
              "serverVersion":"1",
              "userId":"user",
              "username":"viewer",
              "playlistUrl":"m3u-provider://account/account/live",
              "requiresReauthentication":false,
              "unexpectedFutureField":"ignored"
            }
        """.trimIndent()

        val restored = requireNotNull(
            json.decodeFromString<ProviderAccountBackup>(encodedOldEntity).toEntityOrNull()
        )

        assertTrue(restored.requiresReauthentication)
        assertEquals("https://example.com/emby", restored.baseUrl)
    }

    @Test
    fun invalidProviderBaseUrlIsNotBackedUpOrRestored() {
        assertNull(ProviderAccountBackup.fromEntity(account(baseUrl = "file:///data/token")))
        assertNull(
            ProviderAccountBackup(
                id = "account",
                providerId = "provider",
                providerKind = "kind",
                baseUrl = "not a URL",
                serverId = "server",
                serverName = "Media",
                serverVersion = "1",
                userId = "user",
                username = "viewer",
                playlistUrl = "m3u-provider://account/account/live",
            ).toEntityOrNull()
        )
    }

    @Test
    fun accountBackupRejectsPlaybackNamespaceThatDoesNotBelongToAccount() {
        assertNull(
            ProviderAccountBackup.fromEntity(
                account(baseUrl = "https://example.com").copy(
                    playlistUrl = "m3u-provider://account/another-account/live"
                )
            )
        )
    }

    @Test
    fun playbackReferenceBackupContainsOnlyStableIdentifiers() {
        val reference = ChannelPlaybackReference(
            channelId = 7,
            accountId = "account",
            providerId = "provider",
            itemId = "item",
            mediaSourceId = "source",
            sourceType = "live",
        )

        val encoded = json.encodeToString(ProviderPlaybackReferenceBackup.fromEntity(reference))
        val restored = requireNotNull(
            json.decodeFromString<ProviderPlaybackReferenceBackup>(encoded).toEntityOrNull()
        )

        assertFalse(encoded.contains("http"))
        assertEquals(reference, restored)
    }

    @Test
    fun playbackReferenceRestoreUsesWireByteLimits() {
        val valid = ProviderPlaybackReferenceBackup(
            channelId = 7,
            accountId = "account",
            providerId = "provider",
            itemId = "item",
            sourceType = "live",
        )

        assertNull(valid.copy(itemId = "界".repeat(171)).toEntityOrNull())
        assertNull(valid.copy(mediaSourceId = "").toEntityOrNull())
        assertNull(valid.copy(mediaSourceId = "界".repeat(171)).toEntityOrNull())
        assertNull(valid.copy(sourceType = "界".repeat(43)).toEntityOrNull())
        assertNull(valid.copy(sourceType = "x".repeat(129)).toEntityOrNull())
    }

    @Test
    fun providerPlaylistAndChannelBackupRemoveRemoteUrlFields() {
        val playlist = Playlist(
            title = "Media",
            url = "m3u-provider://account/account/live",
            source = DataSource.Provider,
            userAgent = "token-agent",
            epgUrls = listOf("https://example.com/epg?token=secret"),
        ).toProviderBackupCopy()
        val channel = Channel(
            id = 7,
            title = "Channel",
            category = "Live",
            playlistUrl = playlist.url,
            url = "https://example.com/stream?token=secret",
            cover = "https://example.com/cover?token=secret",
            licenseType = Channel.LICENSE_TYPE_CLEAR_KEY,
            licenseKey = "secret-key",
        ).toProviderBackupCopy()

        val encoded = json.encodeToString(playlist) + json.encodeToString(channel)

        assertEquals(Channel.URL_DYNAMIC, channel.url)
        assertNull(channel.cover)
        assertNull(channel.licenseType)
        assertNull(channel.licenseKey)
        assertNull(playlist.userAgent)
        assertTrue(playlist.epgUrls.isEmpty())
        assertFalse(encoded.contains("secret"))
        assertFalse(encoded.contains("token-agent"))
    }

    @Test
    fun restoredProviderRowsAreSanitizedAgain() {
        val playlist = Playlist(
            title = "Media",
            url = "m3u-provider://account/account/live",
            source = DataSource.Provider,
            userAgent = "Bearer restored-secret",
            epgUrls = listOf("https://example.com/epg?token=restored-secret"),
        ).toProviderBackupCopy()
        val channel = Channel(
            id = 7,
            title = "Channel",
            category = "Live",
            playlistUrl = playlist.url,
            url = "https://example.com/live?token=restored-secret",
            cover = "https://example.com/cover?token=restored-secret",
            licenseType = Channel.LICENSE_TYPE_CLEAR_KEY,
            licenseKey = "restored-secret",
        ).toProviderBackupCopy()

        assertNull(playlist.userAgent)
        assertTrue(playlist.epgUrls.isEmpty())
        assertEquals(Channel.URL_DYNAMIC, channel.url)
        assertNull(channel.cover)
        assertNull(channel.licenseType)
        assertNull(channel.licenseKey)
    }

    @Test
    fun providerChannelRestoreRejectsUnsafeOrOversizedIdentityAndDisplayText() {
        val valid = Channel(
            id = 7,
            title = "Channel",
            category = "Live",
            playlistUrl = "m3u-provider://account/account/live",
            url = "https://example.com/live",
            relationId = "remote-7",
        )

        assertEquals(Channel.URL_DYNAMIC, valid.toRestorableProviderBackupCopyOrNull()?.url)
        assertNull(valid.copy(id = 0).toRestorableProviderBackupCopyOrNull())
        assertNull(valid.copy(relationId = null).toRestorableProviderBackupCopyOrNull())
        assertNull(valid.copy(relationId = "界".repeat(171)).toRestorableProviderBackupCopyOrNull())
        assertNull(valid.copy(title = "spoof\u202Etext").toRestorableProviderBackupCopyOrNull())
        assertNull(valid.copy(category = "line\nbreak").toRestorableProviderBackupCopyOrNull())
        assertNull(valid.copy(title = "x".repeat(1_025)).toRestorableProviderBackupCopyOrNull())
    }

    @Test
    fun accountRestoreRejectsInvalidContractIdsAndOversizedFields() {
        val valid = ProviderAccountBackup.fromEntity(account(baseUrl = "https://example.com"))
        requireNotNull(valid)

        assertNull(valid.copy(providerId = "Invalid Provider").toEntityOrNull())
        assertNull(valid.copy(providerKind = "Invalid Kind").toEntityOrNull())
        assertNull(valid.copy(serverName = "x".repeat(1_025)).toEntityOrNull())
        assertNull(
            valid.copy(
                ownerPackageName = "example.plugin",
                ownerServiceName = null,
                ownerCertificateSha256 = "certificate",
            ).toEntityOrNull()
        )
    }

    @Test
    fun providerNamespaceRejectsCredentialsQueriesAndUnexpectedPaths() {
        assertTrue("m3u-provider://account/account/live".isProviderPlaylistNamespace())
        assertFalse("m3u-provider://user@account/account/live".isProviderPlaylistNamespace())
        assertFalse("m3u-provider://account/account/live?token=secret".isProviderPlaylistNamespace())
        assertFalse("m3u-provider://account/account/other".isProviderPlaylistNamespace())
        assertFalse("https://example.com/live".isProviderPlaylistNamespace())
    }

    @Test
    fun playbackReferenceRestoreRequiresMatchingProviderAccountAndChannelGraph() {
        val providerAccount = account(baseUrl = "https://example.com")
        val reference = ProviderPlaybackReferenceBackup(
            channelId = 7,
            accountId = providerAccount.id,
            providerId = providerAccount.providerId,
            itemId = "item",
            sourceType = "live",
        )
        val providerPlaylists = setOf(providerAccount.playlistUrl)

        assertTrue(
            reference.isValidForRestore(
                account = providerAccount,
                channelPlaylistUrl = providerAccount.playlistUrl,
                restoredProviderPlaylistUrls = providerPlaylists,
            )
        )
        assertFalse(
            reference.copy(providerId = "another.provider").isValidForRestore(
                account = providerAccount,
                channelPlaylistUrl = providerAccount.playlistUrl,
                restoredProviderPlaylistUrls = providerPlaylists,
            )
        )
        assertFalse(
            reference.isValidForRestore(
                account = providerAccount,
                channelPlaylistUrl = "m3u-provider://account/another/live",
                restoredProviderPlaylistUrls = providerPlaylists,
            )
        )
    }

    @Test
    fun newReferencePrefixDoesNotMasqueradeAsLegacyReference() {
        val wrapped = BackupOrRestoreContracts.wrapPlaybackReference("{}")

        assertTrue(wrapped.startsWith("Q,"))
        assertEquals("{}", BackupOrRestoreContracts.unwrapPlaybackReference(wrapped))
        assertEquals("{}", BackupOrRestoreContracts.unwrapPlaybackReference("R,{}"))
    }

    @Test
    fun restoreSkipsOnlyProviderAccountsThatConflictWithExistingSubscriptions() {
        val existing = account(baseUrl = "https://target.example")
        val sameRemoteIdentityWithAnotherId = account(
            id = "backup-account",
            baseUrl = "https://backup.example",
        )
        val independent = account(
            id = "independent",
            baseUrl = "https://independent.example",
            serverId = "another-server",
            userId = "another-user",
        )

        val selected = selectRestorableProviderAccounts(
            incoming = listOf(sameRemoteIdentityWithAnotherId, independent),
            existing = listOf(existing),
        )

        assertEquals(listOf(independent), selected)
    }

    @Test
    fun restoreKeepsFirstAccountWhenBackupRepeatsARemoteIdentity() {
        val first = account(id = "first", baseUrl = "https://first.example")
        val duplicate = account(id = "second", baseUrl = "https://second.example")

        val selected = selectRestorableProviderAccounts(
            incoming = listOf(first, duplicate),
            existing = emptyList(),
        )

        assertEquals(listOf(first), selected)
    }

    private fun account(
        id: String = "account",
        baseUrl: String,
        serverId: String = "server",
        userId: String = "user",
    ) = ProviderAccount(
        id = id,
        providerId = "builtin.media-server",
        providerKind = "emby",
        baseUrl = baseUrl,
        serverId = serverId,
        serverName = "Media",
        serverVersion = "1",
        userId = userId,
        username = "viewer",
        playlistUrl = "m3u-provider://account/$id/live",
    )
}
