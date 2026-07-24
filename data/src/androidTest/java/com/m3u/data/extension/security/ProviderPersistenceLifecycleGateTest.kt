package com.m3u.data.extension.security

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.database.model.ProviderPlaybackSessionEntity
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.PlaybackReference
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionChannelDescriptor
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderPersistenceLifecycleGateTest {
    private lateinit var database: M3UDatabase
    private lateinit var importer: SubscriptionProviderImporter
    private lateinit var ownerStore: ProviderAccountOwnerStore
    private lateinit var registry: ActiveExtensionPrincipalRegistry

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val credentialVault = TestCredentialVault()
        importer = SubscriptionProviderImporter(
            database = database,
            playlistDao = database.playlistDao(),
            channelDao = database.channelDao(),
            providerDao = database.providerDao(),
            programmeDao = database.programmeDao(),
            credentialVault = credentialVault,
        )
        ownerStore = ProviderAccountOwnerStore(database, database.providerDao())
        registry = ActiveExtensionPrincipalRegistry().apply { activate(PRINCIPAL) }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun revokeAfterRefreshBeforeInitialImportRejectsTheStaleSnapshot() = runBlocking {
        val lease = checkNotNull(registry.captureLease(EXTENSION_ID))
        val hookReturned = CompletableDeferred<Unit>()
        val allowImport = CompletableDeferred<Unit>()
        val inFlight = async {
            hookReturned.complete(Unit)
            allowImport.await()
            runCatching {
                registry.commit(lease) {
                    importer.importSubscription(
                        title = "Stale provider",
                        source = DataSource.Provider,
                        account = account(),
                        accessToken = "stale-token",
                        refresh = refresh("Stale channel"),
                    )
                }
            }
        }

        hookReturned.await()
        deactivateAndRevoke()
        allowImport.complete(Unit)

        assertTrue(
            inFlight.await().exceptionOrNull() is InactiveExtensionPrincipalLeaseException
        )
        assertNull(database.playlistDao().get(PLAYLIST_URL))
        assertNull(database.providerDao().getAccount(ACCOUNT_ID))
        assertNull(database.providerDao().getCredential(ACCOUNT_ID))
    }

    @Test
    fun revokedExistingAccountCannotBeReauthenticatedByAStaleRefreshWriteback() = runBlocking {
        val account = account()
        importer.importSubscription(
            title = "Original provider",
            source = DataSource.Provider,
            account = account,
            accessToken = "original-token",
            refresh = refresh("Original channel"),
        )
        database.providerDao().insertOrReplace(
            ProviderPlaybackSessionEntity(
                id = SESSION_ID,
                accountId = ACCOUNT_ID,
                providerId = EXTENSION_ID.value,
                itemId = ITEM_ID,
                mediaSourceId = null,
                sourceType = "live",
                playSessionId = "remote-session",
                liveStreamId = "remote-stream",
                createdAtEpochMillis = 1_000L,
            )
        )
        val staleLease = checkNotNull(registry.captureLease(EXTENSION_ID))
        val hookReturned = CompletableDeferred<Unit>()
        val allowImport = CompletableDeferred<Unit>()
        val inFlight = async {
            hookReturned.complete(Unit)
            allowImport.await()
            runCatching {
                registry.commit(staleLease) {
                    importer.importSubscription(
                        title = "Stale refreshed provider",
                        source = DataSource.Provider,
                        account = account.copy(
                            serverName = "Stale refreshed server",
                            requiresReauthentication = false,
                        ),
                        accessToken = "stale-refreshed-token",
                        refresh = refresh("Stale refreshed channel"),
                    )
                }
            }
        }

        hookReturned.await()
        deactivateAndRevoke()
        allowImport.complete(Unit)

        assertTrue(
            inFlight.await().exceptionOrNull() is InactiveExtensionPrincipalLeaseException
        )
        val storedAccount = checkNotNull(database.providerDao().getAccount(ACCOUNT_ID))
        assertEquals("Reference server", storedAccount.serverName)
        assertTrue(storedAccount.requiresReauthentication)
        assertNull(database.providerDao().getCredential(ACCOUNT_ID))
        assertTrue(database.providerDao().getPlaybackSessions().isEmpty())
        assertEquals("Original provider", database.playlistDao().get(PLAYLIST_URL)?.title)
        assertEquals(
            listOf("Original channel"),
            database.channelDao().getByPlaylistUrl(PLAYLIST_URL).map { channel -> channel.title },
        )
    }

    @Test
    fun externalProviderLogoUrlIsIgnoredWithoutFailingImport() = runBlocking {
        assertEquals(
            1,
            importer.importSubscription(
                title = "External provider",
                source = DataSource.Provider,
                account = account(),
                accessToken = "external-token",
                refresh = refresh(
                    channelTitle = "External channel",
                    logoUrl = "https://arbitrary.example/logo.png?tracking=secret",
                ),
            ),
        )

        assertNull(database.channelDao().getByPlaylistUrl(PLAYLIST_URL).single().cover)
        assertEquals(ACCOUNT_ID, database.providerDao().getAccount(ACCOUNT_ID)?.id)
        assertEquals(ACCOUNT_ID, database.providerDao().getCredential(ACCOUNT_ID)?.accountId)
    }

    @Test
    fun builtInProviderLogoUrlRemainsPersisted() = runBlocking {
        val logoUrl = "https://provider.example.test/logo.png"
        importer.importSubscription(
            title = "Built-in provider",
            source = DataSource.Provider,
            account = account().copy(
                ownerPackageName = null,
                ownerServiceName = null,
                ownerCertificateSha256 = null,
            ),
            accessToken = "built-in-token",
            refresh = refresh(
                channelTitle = "Built-in channel",
                logoUrl = logoUrl,
            ),
        )

        assertEquals(
            logoUrl,
            database.channelDao().getByPlaylistUrl(PLAYLIST_URL).single().cover,
        )
    }

    private suspend fun deactivateAndRevoke() {
        assertEquals(
            PRINCIPAL,
            registry.deactivate(
                extensionId = EXTENSION_ID,
                packageName = PRINCIPAL.packageName,
                serviceName = PRINCIPAL.serviceName,
            ),
        )
        registry.awaitPersistence(EXTENSION_ID)
        ownerStore.revoke(OWNER_IDENTITY)
    }

    private fun account() = ProviderAccount(
        id = ACCOUNT_ID,
        providerId = EXTENSION_ID.value,
        providerKind = PROVIDER_KIND.value,
        baseUrl = "https://provider.example.test",
        serverId = SERVER_ID,
        serverName = "Reference server",
        serverVersion = "1.0",
        userId = "viewer-id",
        username = "viewer",
        playlistUrl = PLAYLIST_URL,
        ownerPackageName = PRINCIPAL.packageName,
        ownerServiceName = PRINCIPAL.serviceName,
        ownerCertificateSha256 = PRINCIPAL.certificateSha256,
    )

    private fun refresh(
        channelTitle: String,
        logoUrl: String? = null,
    ) = SubscriptionContentRefreshResult(
        source = SubscriptionSourceDescriptor(
            remoteId = SERVER_ID,
            providerKind = PROVIDER_KIND,
        ),
        channels = listOf(
            SubscriptionChannelDescriptor(
                remoteId = CHANNEL_ID,
                title = channelTitle,
                category = "News",
                logoUrl = logoUrl,
                playbackReference = PlaybackReference(
                    providerId = EXTENSION_ID,
                    itemId = ITEM_ID,
                    sourceType = "live",
                ),
            )
        ),
    )

    private class TestCredentialVault : CredentialVault {
        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ) = ProviderCredentialEntity(
            accountId = accountId,
            credentialHandle = credentialHandle ?: "persistent:$accountId",
            ciphertext = secret,
            nonce = "test-nonce",
            keyVersion = 1,
        )

        override fun decrypt(credential: ProviderCredentialEntity): String = credential.ciphertext
        override fun stage(secret: String): CredentialHandle = error("Not used")
        override fun consume(handle: CredentialHandle): String? = error("Not used")
    }

    private companion object {
        val EXTENSION_ID = ExtensionId("com.m3u.reference.provider")
        val PROVIDER_KIND = ProviderKind("reference")
        val PRINCIPAL = ExtensionPrincipal(
            extensionId = EXTENSION_ID,
            packageName = "com.m3u.reference.extension",
            serviceName = "com.m3u.reference.extension.ProviderService",
            certificateSha256 = "certificate-a",
            uid = 10_001,
        )
        val OWNER_IDENTITY = ExtensionOwnerIdentity(
            extensionId = EXTENSION_ID.value,
            packageName = PRINCIPAL.packageName,
            serviceName = PRINCIPAL.serviceName,
            certificateSha256 = PRINCIPAL.certificateSha256,
        )
        const val ACCOUNT_ID = "account-1"
        const val SERVER_ID = "server-1"
        const val CHANNEL_ID = "channel-1"
        const val ITEM_ID = "item-1"
        const val SESSION_ID = "session-1"
        const val PLAYLIST_URL = "m3u-provider://account/account-1/live"
    }
}
