package com.m3u.data.repository.provider

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
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.extension.security.ProviderBrokerScopeStore
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.PlaybackSessionCloseReason
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.PlaybackSessionDescriptor
import com.m3u.extension.api.subscription.PlaybackSourceResolveRequest
import com.m3u.extension.api.subscription.PlaybackSourceResolveResult
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionProviderSessionCleanupTest {
    @Test
    fun orphanCloseSuccessDeletesSessionAndReportsClosed() = runBlocking {
        withFixture(closeResult = true) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()

            val result = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(
                ProviderSessionCleanupResult(
                    closedCount = 1,
                    pendingCount = 0,
                    recoverablePendingCount = 0,
                ),
                result,
            )
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            assertEquals(
                PlaybackSessionCloseReason.Recovery,
                fixture.extension.closeRequests.single().reason,
            )
        }
    }

    @Test
    fun orphanCloseNotAcknowledgedKeepsSessionAndReportsRecoverablePending() = runBlocking {
        withFixture(closeResult = false) { fixture ->
            fixture.seedAccountAndCredential()
            fixture.seedPlaybackSession()

            val result = fixture.repository.closeOrphanedPlaybackSessions()

            assertEquals(
                ProviderSessionCleanupResult(
                    closedCount = 0,
                    pendingCount = 1,
                    recoverablePendingCount = 1,
                ),
                result,
            )
            assertEquals(
                listOf(SESSION_ID),
                fixture.database.providerDao().getPlaybackSessions().map { session -> session.id },
            )
            assertEquals(
                PlaybackSessionCloseReason.Recovery,
                fixture.extension.closeRequests.single().reason,
            )
        }
    }

    @Test
    fun invalidPlaybackResultClosesReturnedSessionWithoutPersistingIt() = runBlocking {
        val invalidResult = PlaybackSourceResolveResult(
            url = "not-a-playback-url",
            session = PlaybackSessionDescriptor(
                playSessionId = REMOTE_PLAY_SESSION_ID,
                liveStreamId = REMOTE_LIVE_STREAM_ID,
            ),
        )
        withFixture(closeResult = true, resolveResult = invalidResult) { fixture ->
            fixture.seedAccountAndCredential()
            val channelId = fixture.seedPlaybackReference()

            try {
                fixture.repository.resolvePlayback(channelId)
                fail("Expected the invalid playback URL to be rejected")
            } catch (_: ProviderOperationException) {
                // Expected: the original validation failure remains visible after cleanup.
            }

            val closeRequest = fixture.extension.closeRequests.single()
            assertEquals(PlaybackSessionCloseReason.PlaybackFailed, closeRequest.reason)
            assertEquals(REMOTE_PLAY_SESSION_ID, closeRequest.session.playSessionId)
            assertEquals(REMOTE_LIVE_STREAM_ID, closeRequest.session.liveStreamId)
            assertEquals(ITEM_ID, closeRequest.reference.itemId)
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
        }
    }

    private suspend fun withFixture(
        closeResult: Boolean,
        resolveResult: PlaybackSourceResolveResult = VALID_PLAYBACK_RESULT,
        block: suspend (TestFixture) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val credentialVault = TestCredentialVault()
        val extension = TestProviderExtension(
            closeResult = closeResult,
            resolveResult = resolveResult,
        )
        val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
        assertTrue(runtime.register(extension) is ExtensionRegistrationResult.Registered)
        val principalRegistry = ActiveExtensionPrincipalRegistry()
        val repository = SubscriptionProviderRepositoryImpl(
            runtime = runtime,
            providerDao = database.providerDao(),
            playlistDao = database.playlistDao(),
            importer = SubscriptionProviderImporter(
                database = database,
                playlistDao = database.playlistDao(),
                channelDao = database.channelDao(),
                providerDao = database.providerDao(),
                programmeDao = database.programmeDao(),
                credentialVault = credentialVault,
            ),
            credentialVault = credentialVault,
            extensionContributionScheduler = NoOpExtensionContributionScheduler,
            activePrincipalRegistry = principalRegistry,
            providerBrokerScopeStore = ProviderBrokerScopeStore(
                credentialVault = credentialVault,
                principalRegistry = principalRegistry,
            ),
        )
        try {
            block(
                TestFixture(
                    database = database,
                    repository = repository,
                    credentialVault = credentialVault,
                    extension = extension,
                )
            )
        } finally {
            database.close()
        }
    }

    private data class TestFixture(
        val database: M3UDatabase,
        val repository: SubscriptionProviderRepositoryImpl,
        val credentialVault: TestCredentialVault,
        val extension: TestProviderExtension,
    ) {
        suspend fun seedAccountAndCredential() {
            database.playlistDao().insertOrReplace(
                Playlist(
                    title = "Test provider",
                    url = PLAYLIST_URL,
                    source = DataSource.Provider,
                )
            )
            database.providerDao().insertOrReplace(TEST_ACCOUNT)
            database.providerDao().insertOrReplace(
                credentialVault.encrypt(
                    accountId = ACCOUNT_ID,
                    secret = "test-token",
                )
            )
        }

        suspend fun seedPlaybackSession() {
            database.providerDao().insertOrReplace(TEST_SESSION)
        }

        suspend fun seedPlaybackReference(): Int {
            val channelId = database.channelDao().insertOrReplace(
                Channel(
                    url = Channel.URL_DYNAMIC,
                    category = "Test",
                    title = "Test channel",
                    playlistUrl = PLAYLIST_URL,
                    relationId = ITEM_ID,
                )
            ).toInt()
            database.providerDao().insertOrReplace(
                ChannelPlaybackReference(
                    channelId = channelId,
                    accountId = ACCOUNT_ID,
                    providerId = EXTENSION_ID.value,
                    itemId = ITEM_ID,
                    mediaSourceId = MEDIA_SOURCE_ID,
                    sourceType = SOURCE_TYPE,
                    fallbackDirectUrl = null,
                )
            )
            return channelId
        }
    }

    private class TestProviderExtension(
        private val closeResult: Boolean,
        private val resolveResult: PlaybackSourceResolveResult,
    ) : ExtensionEntrypoint {
        val closeRequests = mutableListOf<PlaybackSessionCloseRequest>()

        override val manifest = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Provider session test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.ResolvePlayback.hook,
                    schemaVersion = SubscriptionHookSpecs.ResolvePlayback.schemaVersion,
                    requiredCapabilities = REQUIRED_CAPABILITIES,
                ),
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.ClosePlayback.hook,
                    schemaVersion = SubscriptionHookSpecs.ClosePlayback.schemaVersion,
                    requiredCapabilities = REQUIRED_CAPABILITIES,
                ),
            ),
            capabilities = REQUIRED_CAPABILITIES.mapTo(mutableSetOf()) { capability ->
                ExtensionCapabilityRequest(
                    capability = capability,
                    reason = "Exercise provider playback session lifecycle",
                )
            },
        )

        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<PlaybackSourceResolveRequest, PlaybackSourceResolveResult> {
                override val spec = SubscriptionHookSpecs.ResolvePlayback

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: PlaybackSourceResolveRequest,
                ): HookResult<PlaybackSourceResolveResult> = HookResult.Success(resolveResult)
            },
            object : ExtensionHandler<PlaybackSessionCloseRequest, PlaybackSessionCloseResult> {
                override val spec = SubscriptionHookSpecs.ClosePlayback

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: PlaybackSessionCloseRequest,
                ): HookResult<PlaybackSessionCloseResult> {
                    closeRequests += request
                    return HookResult.Success(PlaybackSessionCloseResult(closed = closeResult))
                }
            },
        )
    }

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

    private data object NoOpExtensionContributionScheduler : ExtensionContributionScheduler {
        override fun enqueue(playlistUrl: String) = Unit
    }

    private companion object {
        val EXTENSION_ID = ExtensionId("com.m3u.test.provider.session")
        val PROVIDER_KIND = ProviderKind("test")
        val REQUIRED_CAPABILITIES = setOf(
            ExtensionCapabilityIds.Network,
            ExtensionCapabilityIds.CredentialRead,
            ExtensionCapabilityIds.PlaybackResolve,
        )
        const val ACCOUNT_ID = "account-1"
        const val PLAYLIST_URL = "m3u-provider://account/account-1/live"
        const val ITEM_ID = "item-1"
        const val MEDIA_SOURCE_ID = "media-source-1"
        const val SOURCE_TYPE = "live"
        const val SESSION_ID = "session-1"
        const val REMOTE_PLAY_SESSION_ID = "remote-play-session-1"
        const val REMOTE_LIVE_STREAM_ID = "remote-live-stream-1"

        val TEST_ACCOUNT = ProviderAccount(
            id = ACCOUNT_ID,
            providerId = EXTENSION_ID.value,
            providerKind = PROVIDER_KIND.value,
            baseUrl = "https://media.example.test",
            serverId = "server-1",
            serverName = "Test server",
            serverVersion = "1.0",
            userId = "user-1",
            username = "test-user",
            playlistUrl = PLAYLIST_URL,
        )
        val TEST_SESSION = ProviderPlaybackSessionEntity(
            id = SESSION_ID,
            accountId = ACCOUNT_ID,
            providerId = EXTENSION_ID.value,
            itemId = ITEM_ID,
            mediaSourceId = MEDIA_SOURCE_ID,
            sourceType = SOURCE_TYPE,
            fallbackDirectUrl = null,
            playSessionId = REMOTE_PLAY_SESSION_ID,
            liveStreamId = REMOTE_LIVE_STREAM_ID,
            createdAtEpochMillis = 1L,
        )
        val VALID_PLAYBACK_RESULT = PlaybackSourceResolveResult(
            url = "https://media.example.test/live.m3u8",
        )
    }
}
