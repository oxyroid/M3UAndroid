package com.m3u.data.repository.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.dao.ProviderDao
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
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.PlaybackSessionCloseReason
import com.m3u.extension.api.subscription.PlaybackSessionCloseRequest
import com.m3u.extension.api.subscription.PlaybackSessionCloseResult
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.ProviderValidationEvidence
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.api.subscription.SubscriptionProviderErrorCodes
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderValidateRequest
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.api.subscription.ValidatedProviderAccount
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionProviderReauthenticationSessionSafetyTest {
    @Test
    fun reauthenticationClosesSessionWithOldAccountAndCredentialBeforePersistingReplacement() =
        runBlocking {
            withFixture(closeBehavior = CloseBehavior.CONFIRMED) { fixture ->
                fixture.seedExistingAccount(OldCredentialState.USABLE)

                val result = fixture.reauthenticate()

                assertEquals(PLAYLIST_URL, result.playlistUrl)
                assertEquals(0, result.channelCount)
                val close = fixture.extension.closeObservations.single()
                assertEquals(OLD_BASE_URL, close.request.account.baseUrl)
                assertEquals(OLD_USERNAME, close.request.account.username)
                assertEquals(PlaybackSessionCloseReason.Stopped, close.request.reason)
                assertEquals(REMOTE_PLAY_SESSION_ID, close.request.session.playSessionId)
                assertEquals(OLD_BASE_URL, close.persistedAccount?.baseUrl)
                assertEquals(OLD_TOKEN, close.persistedCredentialSecret)
                assertEquals(
                    listOf(
                        "validate:$NEW_TOKEN",
                        "refresh:$NEW_TOKEN",
                        "close:$OLD_TOKEN",
                    ),
                    fixture.extension.events,
                )

                fixture.assertReplacementPersisted()
                assertNull(fixture.database.providerDao().getPlaybackSession(SESSION_ID))
            }
        }

    @Test
    fun missingOldCredentialClearsSessionWithoutClosingWithNewCredential() = runBlocking {
        withFixture(closeBehavior = CloseBehavior.CONFIRMED) { fixture ->
            fixture.seedExistingAccount(OldCredentialState.MISSING)

            fixture.reauthenticate()

            assertTrue(fixture.extension.closeObservations.isEmpty())
            assertEquals(
                listOf("validate:$NEW_TOKEN", "refresh:$NEW_TOKEN"),
                fixture.extension.events,
            )
            assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
            fixture.assertReplacementPersisted()
        }
    }

    @Test
    fun accountAlreadyRequiringReauthenticationClearsSessionWithoutUsingItsOldOrNewToken() =
        runBlocking {
            withFixture(closeBehavior = CloseBehavior.CONFIRMED) { fixture ->
                fixture.seedExistingAccount(OldCredentialState.REAUTHENTICATION_REQUIRED)

                fixture.reauthenticate()

                assertTrue(fixture.extension.closeObservations.isEmpty())
                assertEquals(
                    listOf("validate:$NEW_TOKEN", "refresh:$NEW_TOKEN"),
                    fixture.extension.events,
                )
                assertTrue(fixture.database.providerDao().getPlaybackSessions().isEmpty())
                fixture.assertReplacementPersisted()
            }
        }

    @Test
    fun unconfirmedOldSessionCloseBlocksReplacementAndKeepsOldSession() = runBlocking {
        withFixture(closeBehavior = CloseBehavior.PENDING) { fixture ->
            fixture.seedExistingAccount(OldCredentialState.USABLE)

            val error = fixture.expectReauthenticationFailure()

            assertEquals("provider.session_close_pending", error.code)
            assertTrue(error.recoverable)
            fixture.assertOldPersistenceRetained()
            assertEquals(OLD_TOKEN, fixture.extension.closeObservations.single().persistedCredentialSecret)
        }
    }

    @Test
    fun nonAuthenticationCloseFailureBlocksReplacementAndKeepsOldSession() = runBlocking {
        withFixture(closeBehavior = CloseBehavior.NON_AUTHENTICATION_FAILURE) { fixture ->
            fixture.seedExistingAccount(OldCredentialState.USABLE)

            val error = fixture.expectReauthenticationFailure()

            assertEquals(CLOSE_FAILURE_CODE.value, error.code)
            assertFalse(error.recoverable)
            fixture.assertOldPersistenceRetained()
            assertEquals(OLD_TOKEN, fixture.extension.closeObservations.single().persistedCredentialSecret)
        }
    }

    private suspend fun withFixture(
        closeBehavior: CloseBehavior,
        block: suspend (TestFixture) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val credentialVault = TestCredentialVault()
        val extension = TestProviderExtension(
            providerDao = database.providerDao(),
            credentialVault = credentialVault,
            closeBehavior = closeBehavior,
        )
        val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
        assertTrue(runtime.register(extension) is ExtensionRegistrationResult.Registered)
        val principalRegistry = ActiveExtensionPrincipalRegistry()
        val repository = SubscriptionProviderRepositoryImpl(
            context = context,
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
            extensionContributionRunCoordinator = ExtensionContributionRunCoordinator(),
            activePrincipalRegistry = principalRegistry,
            providerBrokerScopeStore = ProviderBrokerScopeStore(
                credentialVault = credentialVault,
                principalRegistry = principalRegistry,
            ),
            lifecycleCoordinator = ProviderLifecycleCoordinator(),
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
        suspend fun seedExistingAccount(state: OldCredentialState) {
            database.playlistDao().insertOrReplace(
                Playlist(
                    title = OLD_PLAYLIST_TITLE,
                    url = PLAYLIST_URL,
                    source = DataSource.Provider,
                )
            )
            database.providerDao().insertOrReplace(
                OLD_ACCOUNT.copy(
                    requiresReauthentication =
                        state == OldCredentialState.REAUTHENTICATION_REQUIRED,
                )
            )
            if (state != OldCredentialState.MISSING) {
                database.providerDao().insertOrReplace(
                    credentialVault.encrypt(
                        accountId = ACCOUNT_ID,
                        secret = OLD_TOKEN,
                    )
                )
            }
            database.providerDao().insertOrReplace(OLD_SESSION)
        }

        suspend fun reauthenticate(): ProviderSubscriptionResult {
            val submittedCredential = repository.stageCredential(NEW_TOKEN)
            return repository.subscribe(
                ProviderSubscriptionRequest(
                    title = NEW_PLAYLIST_TITLE,
                    providerId = EXTENSION_ID,
                    providerKind = PROVIDER_KIND,
                    settingValues = mapOf(
                        SubscriptionProviderSettingKeys.BaseUrl to NEW_BASE_URL,
                        SubscriptionProviderSettingKeys.Username to NEW_USERNAME,
                    ),
                    credentialHandles = mapOf(
                        SubscriptionProviderSettingKeys.Password to submittedCredential,
                    ),
                )
            )
        }

        suspend fun expectReauthenticationFailure(): ProviderOperationException = try {
            reauthenticate()
            fail("Expected the existing playback session to block account replacement")
            error("Unreachable")
        } catch (error: ProviderOperationException) {
            error
        }

        suspend fun assertReplacementPersisted() {
            val account = requireNotNull(database.providerDao().getAccount(ACCOUNT_ID))
            val credential = requireNotNull(database.providerDao().getCredential(ACCOUNT_ID))
            assertEquals(NEW_BASE_URL, account.baseUrl)
            assertEquals(NEW_USERNAME, account.username)
            assertFalse(account.requiresReauthentication)
            assertEquals(NEW_PLAYLIST_TITLE, database.playlistDao().get(PLAYLIST_URL)?.title)
            assertEquals(NEW_TOKEN, credentialVault.decrypt(credential))
        }

        suspend fun assertOldPersistenceRetained() {
            val account = requireNotNull(database.providerDao().getAccount(ACCOUNT_ID))
            val credential = requireNotNull(database.providerDao().getCredential(ACCOUNT_ID))
            assertEquals(OLD_BASE_URL, account.baseUrl)
            assertEquals(OLD_USERNAME, account.username)
            assertFalse(account.requiresReauthentication)
            assertEquals(OLD_PLAYLIST_TITLE, database.playlistDao().get(PLAYLIST_URL)?.title)
            assertEquals(OLD_TOKEN, credentialVault.decrypt(credential))
            assertNotNull(database.providerDao().getPlaybackSession(SESSION_ID))
        }
    }

    private class TestProviderExtension(
        private val providerDao: ProviderDao,
        private val credentialVault: TestCredentialVault,
        private val closeBehavior: CloseBehavior,
    ) : ExtensionEntrypoint {
        val events = mutableListOf<String>()
        val closeObservations = mutableListOf<CloseObservation>()

        override val manifest = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Reauthentication session safety provider",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.Discover.hook,
                    schemaVersion = SubscriptionHookSpecs.Discover.schemaVersion,
                ),
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.Validate.hook,
                    schemaVersion = SubscriptionHookSpecs.Validate.schemaVersion,
                    requiredCapabilities = setOf(
                        ExtensionCapabilityIds.Network,
                        ExtensionCapabilityIds.CredentialWrite,
                    ),
                ),
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.Refresh.hook,
                    schemaVersion = SubscriptionHookSpecs.Refresh.schemaVersion,
                    requiredCapabilities = setOf(
                        ExtensionCapabilityIds.Network,
                        ExtensionCapabilityIds.CredentialRead,
                        ExtensionCapabilityIds.SubscriptionRead,
                    ),
                ),
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.ClosePlayback.hook,
                    schemaVersion = SubscriptionHookSpecs.ClosePlayback.schemaVersion,
                    requiredCapabilities = setOf(
                        ExtensionCapabilityIds.Network,
                        ExtensionCapabilityIds.CredentialRead,
                        ExtensionCapabilityIds.PlaybackResolve,
                    ),
                ),
            ),
            capabilities = REQUIRED_CAPABILITIES.mapTo(mutableSetOf()) { capability ->
                ExtensionCapabilityRequest(
                    capability = capability,
                    reason = "Exercise provider reauthentication session safety",
                )
            },
        )

        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object :
                ExtensionHandler<
                    SubscriptionProviderDiscoverRequest,
                    SubscriptionProviderDiscoverResult,
                    > {
                override val spec = SubscriptionHookSpecs.Discover

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: SubscriptionProviderDiscoverRequest,
                ) = HookResult.Success(
                    SubscriptionProviderDiscoverResult(provider = PROVIDER_DESCRIPTOR)
                )
            },
            object :
                ExtensionHandler<
                    SubscriptionProviderValidateRequest,
                    SubscriptionProviderValidateResult,
                    > {
                override val spec = SubscriptionHookSpecs.Validate

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: SubscriptionProviderValidateRequest,
                ): HookResult<SubscriptionProviderValidateResult> {
                    val submittedHandle =
                        request.credentialHandles[SubscriptionProviderSettingKeys.Password]
                            ?: return authenticationFailure()
                    val submittedToken = credentialVault.consume(submittedHandle)
                        ?: return authenticationFailure()
                    events += "validate:$submittedToken"
                    return HookResult.Success(
                        SubscriptionProviderValidateResult(
                            evidence = ProviderValidationEvidence.TrustedDirect(
                                account = ValidatedProviderAccount(
                                    normalizedBaseUrl = request.settingValues.getValue(
                                        SubscriptionProviderSettingKeys.BaseUrl
                                    ),
                                    detectedKind = request.providerKind,
                                    serverId = SERVER_ID,
                                    serverName = NEW_SERVER_NAME,
                                    serverVersion = NEW_SERVER_VERSION,
                                    userId = USER_ID,
                                    username = request.settingValues.getValue(
                                        SubscriptionProviderSettingKeys.Username
                                    ),
                                ),
                                credential = credentialVault.stage(submittedToken),
                            )
                        )
                    )
                }
            },
            object :
                ExtensionHandler<
                    SubscriptionContentRefreshRequest,
                    SubscriptionContentRefreshResult,
                    > {
                override val spec = SubscriptionHookSpecs.Refresh

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: SubscriptionContentRefreshRequest,
                ): HookResult<SubscriptionContentRefreshResult> {
                    val token = resolveCredential(request.credential.handle)
                        ?: return authenticationFailure()
                    events += "refresh:$token"
                    return HookResult.Success(REFRESH_RESULT)
                }
            },
            object :
                ExtensionHandler<
                    PlaybackSessionCloseRequest,
                    PlaybackSessionCloseResult,
                    > {
                override val spec = SubscriptionHookSpecs.ClosePlayback

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: PlaybackSessionCloseRequest,
                ): HookResult<PlaybackSessionCloseResult> {
                    val persistedAccount = providerDao.getAccount(request.account.accountId)
                    val persistedCredential = providerDao.getCredential(request.account.accountId)
                    val credentialSecret = resolveCredential(request.credential.handle)
                    closeObservations += CloseObservation(
                        request = request,
                        persistedAccount = persistedAccount,
                        persistedCredentialSecret = persistedCredential?.let(
                            credentialVault::decrypt
                        ),
                    )
                    events += "close:$credentialSecret"
                    return when (closeBehavior) {
                        CloseBehavior.CONFIRMED ->
                            HookResult.Success(PlaybackSessionCloseResult(closed = true))

                        CloseBehavior.PENDING ->
                            HookResult.Success(PlaybackSessionCloseResult(closed = false))

                        CloseBehavior.NON_AUTHENTICATION_FAILURE -> HookResult.Failure(
                            ExtensionError(
                                code = CLOSE_FAILURE_CODE,
                                message = "Remote session close failed",
                                recoverable = false,
                            )
                        )
                    }
                }
            },
        )

        private suspend fun resolveCredential(handle: CredentialHandle): String? {
            credentialVault.consume(handle)?.let { return it }
            val persisted = providerDao.getCredentialByHandle(handle.value) ?: return null
            return credentialVault.decrypt(persisted)
        }

        private fun <T : ExtensionPayload> authenticationFailure():
            HookResult<T> = HookResult.Failure(
                ExtensionError(
                    code = SubscriptionProviderErrorCodes.AuthenticationFailed,
                    message = "Provider credentials were rejected",
                    recoverable = false,
                )
            )
    }

    private class TestCredentialVault : CredentialVault {
        private val transientSecrets = linkedMapOf<String, String>()
        private var transientSequence = 0
        private var persistentSequence = 0

        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ) = ProviderCredentialEntity(
            accountId = accountId,
            credentialHandle = credentialHandle
                ?: "persistent:${++persistentSequence}:$accountId",
            ciphertext = secret,
            nonce = "test-nonce",
            keyVersion = 1,
        )

        override fun decrypt(credential: ProviderCredentialEntity): String = credential.ciphertext

        override fun stage(secret: String): CredentialHandle {
            val handle = CredentialHandle("transient:${++transientSequence}")
            transientSecrets[handle.value] = secret
            return handle
        }

        override fun consume(handle: CredentialHandle): String? =
            transientSecrets.remove(handle.value)
    }

    private data class CloseObservation(
        val request: PlaybackSessionCloseRequest,
        val persistedAccount: ProviderAccount?,
        val persistedCredentialSecret: String?,
    )

    private enum class OldCredentialState {
        USABLE,
        MISSING,
        REAUTHENTICATION_REQUIRED,
    }

    private enum class CloseBehavior {
        CONFIRMED,
        PENDING,
        NON_AUTHENTICATION_FAILURE,
    }

    private data object NoOpExtensionContributionScheduler : ExtensionContributionScheduler {
        override suspend fun enqueue(playlistUrl: String) = Unit

        override suspend fun cancel(playlistUrl: String) = Unit
    }

    private companion object {
        val EXTENSION_ID = ExtensionId("com.m3u.test.provider.reauthentication")
        val PROVIDER_KIND = ProviderKind("test")
        val CLOSE_FAILURE_CODE = ExtensionErrorCode("provider.close_failed")
        val REQUIRED_CAPABILITIES = setOf(
            ExtensionCapabilityIds.Network,
            ExtensionCapabilityIds.CredentialRead,
            ExtensionCapabilityIds.CredentialWrite,
            ExtensionCapabilityIds.SubscriptionRead,
            ExtensionCapabilityIds.PlaybackResolve,
        )
        const val ACCOUNT_ID = "account-1"
        const val PLAYLIST_URL = "m3u-provider://account/account-1/live"
        const val OLD_PLAYLIST_TITLE = "Old provider"
        const val NEW_PLAYLIST_TITLE = "Reauthenticated provider"
        const val OLD_BASE_URL = "https://old.example.test"
        const val NEW_BASE_URL = "https://new.example.test"
        const val SERVER_ID = "server-1"
        const val USER_ID = "user-1"
        const val OLD_USERNAME = "old-user"
        const val NEW_USERNAME = "new-user"
        const val NEW_SERVER_NAME = "New server"
        const val NEW_SERVER_VERSION = "2.0"
        const val OLD_TOKEN = "old-token"
        const val NEW_TOKEN = "new-token"
        const val SESSION_ID = "session-1"
        const val REMOTE_PLAY_SESSION_ID = "remote-play-session-1"

        val PROVIDER_DESCRIPTOR = SubscriptionProviderDescriptor(
            providerId = EXTENSION_ID,
            displayName = "Test provider",
            variants = listOf(
                SubscriptionProviderVariant(
                    kind = PROVIDER_KIND,
                    displayName = "Test",
                )
            ),
            settingsSchema = ExtensionSettingSchema(
                version = 1,
                fields = listOf(
                    ExtensionSettingField(
                        key = SubscriptionProviderSettingKeys.BaseUrl,
                        label = "Server URL",
                        type = ExtensionSettingType.TEXT,
                        required = true,
                    ),
                    ExtensionSettingField(
                        key = SubscriptionProviderSettingKeys.Username,
                        label = "Username",
                        type = ExtensionSettingType.TEXT,
                        required = true,
                    ),
                    ExtensionSettingField(
                        key = SubscriptionProviderSettingKeys.Password,
                        label = "Password",
                        type = ExtensionSettingType.SECRET,
                        required = true,
                    ),
                ),
            ),
        )
        val OLD_ACCOUNT = ProviderAccount(
            id = ACCOUNT_ID,
            providerId = EXTENSION_ID.value,
            providerKind = PROVIDER_KIND.value,
            baseUrl = OLD_BASE_URL,
            serverId = SERVER_ID,
            serverName = "Old server",
            serverVersion = "1.0",
            userId = USER_ID,
            username = OLD_USERNAME,
            playlistUrl = PLAYLIST_URL,
        )
        val OLD_SESSION = ProviderPlaybackSessionEntity(
            id = SESSION_ID,
            accountId = ACCOUNT_ID,
            providerId = EXTENSION_ID.value,
            itemId = "item-1",
            mediaSourceId = "media-source-1",
            sourceType = "live",
            playSessionId = REMOTE_PLAY_SESSION_ID,
            liveStreamId = "remote-live-stream-1",
            createdAtEpochMillis = 1L,
        )
        val REFRESH_RESULT = SubscriptionContentRefreshResult(
            source = SubscriptionSourceDescriptor(
                remoteId = SERVER_ID,
                providerKind = PROVIDER_KIND,
            ),
            channels = emptyList(),
        )
    }
}
