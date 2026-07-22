package com.m3u.data.extension.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ChannelPlaybackReference
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.ProviderPlaybackSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderAccountOwnerStoreTest {
    private lateinit var database: M3UDatabase
    private lateinit var store: ProviderAccountOwnerStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = ProviderAccountOwnerStore(database, database.providerDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun revokeDeletesOnlyExactOwnerSecretsAndSessionsWhilePreservingHostData() = runBlocking {
        val target = seedOwnership(
            id = "target",
            extensionId = EXTENSION_ID,
            packageName = PACKAGE_NAME,
            serviceName = SERVICE_NAME,
            certificateSha256 = CERTIFICATE_SHA256,
        )
        val differentCertificate = seedOwnership(
            id = "different-certificate",
            extensionId = EXTENSION_ID,
            packageName = PACKAGE_NAME,
            serviceName = SERVICE_NAME,
            certificateSha256 = OTHER_CERTIFICATE_SHA256,
        )
        val differentService = seedOwnership(
            id = "different-service",
            extensionId = EXTENSION_ID,
            packageName = PACKAGE_NAME,
            serviceName = OTHER_SERVICE_NAME,
            certificateSha256 = CERTIFICATE_SHA256,
        )
        val differentPackage = seedOwnership(
            id = "different-package",
            extensionId = EXTENSION_ID,
            packageName = OTHER_PACKAGE_NAME,
            serviceName = SERVICE_NAME,
            certificateSha256 = CERTIFICATE_SHA256,
        )
        val differentExtension = seedOwnership(
            id = "different-extension",
            extensionId = OTHER_EXTENSION_ID,
            packageName = PACKAGE_NAME,
            serviceName = SERVICE_NAME,
            certificateSha256 = CERTIFICATE_SHA256,
        )
        val builtIn = seedOwnership(
            id = "built-in",
            extensionId = EXTENSION_ID,
            packageName = null,
            serviceName = null,
            certificateSha256 = null,
        )

        val result = store.revoke(
            ExtensionOwnerIdentity(
                extensionId = EXTENSION_ID,
                packageName = PACKAGE_NAME,
                serviceName = SERVICE_NAME,
                certificateSha256 = CERTIFICATE_SHA256,
            )
        )

        assertEquals(
            ProviderOwnerRevocation(
                affectedAccounts = 1,
                deletedCredentials = 1,
                deletedPlaybackSessions = 1,
            ),
            result,
        )
        assertOwnership(
            target,
            requiresReauthentication = true,
            hasCredential = false,
            hasOpenSession = false,
        )
        listOf(
            differentCertificate,
            differentService,
            differentPackage,
            differentExtension,
            builtIn,
        ).forEach { ownership ->
            assertOwnership(
                ownership,
                requiresReauthentication = false,
                hasCredential = true,
                hasOpenSession = true,
            )
        }
    }

    @Test
    fun restoringAccountInvalidatesExistingCredentialAndOpenSession() = runBlocking {
        val ownership = seedOwnership(
            id = "restored",
            extensionId = EXTENSION_ID,
            packageName = PACKAGE_NAME,
            serviceName = SERVICE_NAME,
            certificateSha256 = CERTIFICATE_SHA256,
        )
        val restored = requireNotNull(database.providerDao().getAccount(ownership.accountId)).copy(
            baseUrl = "https://restored.example.test",
            requiresReauthentication = false,
        )

        database.providerDao().restoreReauthenticationRequiredAccount(restored)

        assertEquals(
            "https://restored.example.test",
            database.providerDao().getAccount(ownership.accountId)?.baseUrl,
        )
        assertOwnership(
            ownership = ownership,
            requiresReauthentication = true,
            hasCredential = false,
            hasOpenSession = false,
        )
    }

    private suspend fun seedOwnership(
        id: String,
        extensionId: String,
        packageName: String?,
        serviceName: String?,
        certificateSha256: String?,
    ): SeededOwnership {
        val playlistUrl = "m3u-provider://account/$id/live"
        database.playlistDao().insertOrReplace(
            Playlist(
                title = "Provider $id",
                url = playlistUrl,
                source = DataSource.Provider,
            )
        )
        database.providerDao().insertOrReplace(
            ProviderAccount(
                id = id,
                providerId = extensionId,
                providerKind = "reference",
                baseUrl = "https://$id.example.test",
                serverId = "server-$id",
                serverName = "Server $id",
                serverVersion = "1.0",
                userId = "user-$id",
                username = "viewer-$id",
                playlistUrl = playlistUrl,
                ownerPackageName = packageName,
                ownerServiceName = serviceName,
                ownerCertificateSha256 = certificateSha256,
            )
        )
        database.providerDao().insertOrReplace(
            ProviderCredentialEntity(
                accountId = id,
                credentialHandle = "credential-$id",
                ciphertext = "ciphertext-$id",
                nonce = "nonce-$id",
                keyVersion = 1,
            )
        )
        val channelReference = "channel-$id"
        val channelId = database.channelDao().insertOrReplace(
            Channel(
                url = Channel.URL_DYNAMIC,
                category = "Live",
                title = "Channel $id",
                playlistUrl = playlistUrl,
                relationId = channelReference,
            )
        ).toInt()
        database.providerDao().insertOrReplace(
            ChannelPlaybackReference(
                channelId = channelId,
                accountId = id,
                providerId = extensionId,
                itemId = "item-$id",
                mediaSourceId = null,
                sourceType = "live",
                fallbackDirectUrl = null,
            )
        )
        val sessionId = "session-$id"
        database.providerDao().insertOrReplace(
            ProviderPlaybackSessionEntity(
                id = sessionId,
                accountId = id,
                providerId = extensionId,
                itemId = "item-$id",
                mediaSourceId = null,
                sourceType = "live",
                fallbackDirectUrl = null,
                playSessionId = "remote-$id",
                liveStreamId = "live-$id",
                createdAtEpochMillis = 1_000,
            )
        )
        return SeededOwnership(
            accountId = id,
            playlistUrl = playlistUrl,
            channelId = channelId,
            channelReference = channelReference,
            sessionId = sessionId,
            extensionId = extensionId,
            packageName = packageName,
            serviceName = serviceName,
            certificateSha256 = certificateSha256,
        )
    }

    private suspend fun assertOwnership(
        ownership: SeededOwnership,
        requiresReauthentication: Boolean,
        hasCredential: Boolean,
        hasOpenSession: Boolean,
    ) {
        val account = database.providerDao().getAccount(ownership.accountId)
        assertNotNull(account)
        assertEquals(requiresReauthentication, account?.requiresReauthentication)
        assertEquals(ownership.extensionId, account?.providerId)
        assertEquals(ownership.packageName, account?.ownerPackageName)
        assertEquals(ownership.serviceName, account?.ownerServiceName)
        assertEquals(ownership.certificateSha256, account?.ownerCertificateSha256)
        assertNotNull(database.playlistDao().get(ownership.playlistUrl))
        assertNotNull(
            database.channelDao().getByPlaylistUrlAndRelationId(
                ownership.playlistUrl,
                ownership.channelReference,
            )
        )
        assertNotNull(database.providerDao().getPlaybackReference(ownership.channelId))
        if (hasCredential) {
            assertNotNull(database.providerDao().getCredential(ownership.accountId))
        } else {
            assertNull(database.providerDao().getCredential(ownership.accountId))
        }
        val sessionStillOpen = database.providerDao().getPlaybackSessions()
            .any { session -> session.id == ownership.sessionId }
        if (hasOpenSession) assertTrue(sessionStillOpen) else assertFalse(sessionStillOpen)
    }

    private data class SeededOwnership(
        val accountId: String,
        val playlistUrl: String,
        val channelId: Int,
        val channelReference: String,
        val sessionId: String,
        val extensionId: String,
        val packageName: String?,
        val serviceName: String?,
        val certificateSha256: String?,
    )

    private companion object {
        const val EXTENSION_ID = "com.m3u.example.provider"
        const val OTHER_EXTENSION_ID = "com.m3u.other.provider"
        const val PACKAGE_NAME = "com.m3u.example.extension"
        const val OTHER_PACKAGE_NAME = "com.m3u.other.extension"
        const val SERVICE_NAME = "com.m3u.example.extension.ProviderService"
        const val OTHER_SERVICE_NAME = "com.m3u.example.extension.OtherService"
        const val CERTIFICATE_SHA256 = "certificate-a"
        const val OTHER_CERTIFICATE_SHA256 = "certificate-b"
    }
}
