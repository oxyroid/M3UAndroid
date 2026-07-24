package com.m3u.data.repository.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.extension.security.ExtensionPrincipal
import com.m3u.data.extension.security.ProviderBrokerScopeStore
import com.m3u.data.extension.security.ProviderCredentialMaterial
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderAuthenticationContextKeys
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.ProviderValidationEvidence
import com.m3u.extension.api.subscription.SubscriptionContentRefreshRequest
import com.m3u.extension.api.subscription.SubscriptionContentRefreshResult
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderValidateResult
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.extension.api.subscription.SubscriptionSourceDescriptor
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionProviderRestoredOwnerClaimTest {
    @Test
    fun explicitReauthenticationTransfersRestoredAccountToCurrentExternalPrincipal() = runBlocking {
        withFixture { fixture ->
            val restored = fixture.createRestoredAccount()
            assertTrue(
                fixture.repository.observeAccountSummaries()
                    .first()
                    .single()
                    .requiresExtensionOwnerConfirmation
            )
            fixture.switchPrincipal(OLD_PRINCIPAL, CLAIMANT_PRINCIPAL)

            val result = fixture.claim(restored.playlistUrl, title = CLAIMED_TITLE)

            assertEquals(restored.playlistUrl, result.playlistUrl)
            val claimed = requireNotNull(fixture.database.providerDao().getAccount(restored.id))
            assertEquals(restored.id, claimed.id)
            assertEquals(restored.playlistUrl, claimed.playlistUrl)
            assertTrue(CLAIMANT_PRINCIPAL.owns(claimed))
            assertFalse(claimed.requiresReauthentication)
            assertEquals(CLAIMED_TITLE, fixture.database.playlistDao().get(restored.playlistUrl)?.title)
            assertFalse(
                fixture.repository.observeAccountSummaries()
                    .first()
                    .single()
                    .requiresExtensionOwnerConfirmation
            )
            assertEquals(
                fixture.transport.tokenFor(CLAIMANT_PRINCIPAL),
                fixture.persistedPrimaryCredential(claimed.id),
            )
        }
    }

    @Test
    fun explicitReauthenticationRejectsAccountOwnedByAnotherProviderId() = runBlocking {
        withFixture { fixture ->
            val restored = fixture.createRestoredAccount()
            val mismatched = restored.copy(providerId = OTHER_PROVIDER_ID.value)
            fixture.database.providerDao().insertOrReplace(mismatched)
            fixture.switchPrincipal(OLD_PRINCIPAL, CLAIMANT_PRINCIPAL)

            val failure = captureFailure {
                fixture.claim(restored.playlistUrl, title = CLAIMED_TITLE)
            }

            assertTrue(failure is ProviderOperationException)
            assertEquals(mismatched, fixture.database.providerDao().getAccount(restored.id))
            assertNull(fixture.database.providerDao().getCredential(restored.id))
        }
    }

    @Test
    fun explicitReauthenticationRejectsDifferentRemoteIdentity() = runBlocking {
        withFixture { fixture ->
            val restored = fixture.createRestoredAccount()
            fixture.switchPrincipal(OLD_PRINCIPAL, CLAIMANT_PRINCIPAL)
            fixture.transport.remoteServerIdentity = DIFFERENT_REMOTE_SERVER_ID

            val failure = captureFailure {
                fixture.claim(restored.playlistUrl, title = CLAIMED_TITLE)
            }

            assertTrue(failure is ProviderOperationException)
            assertEquals(
                "The authenticated provider account does not match the selected subscription",
                failure.message,
            )
            assertEquals(restored, fixture.database.providerDao().getAccount(restored.id))
            assertNull(fixture.database.providerDao().getCredential(restored.id))
        }
    }

    @Test
    fun concurrentClaimWithRevokedLeaseCannotOverwriteNewActivePrincipal() = runBlocking {
        withFixture { fixture ->
            val restored = fixture.createRestoredAccount()
            fixture.switchPrincipal(OLD_PRINCIPAL, CLAIMANT_PRINCIPAL)
            val firstRefresh = fixture.transport.blockNextRefresh()
            val firstClaim = async(Dispatchers.Default) {
                runCatching {
                    fixture.claim(restored.playlistUrl, title = FIRST_CLAIM_TITLE)
                }
            }
            withTimeout(5_000L) {
                firstRefresh.entered.await()
            }

            fixture.switchPrincipal(CLAIMANT_PRINCIPAL, WINNING_PRINCIPAL)
            val secondValidation = fixture.transport.signalNextValidation()
            val secondClaim = async(Dispatchers.Default) {
                runCatching {
                    fixture.claim(restored.playlistUrl, title = WINNING_CLAIM_TITLE)
                }
            }
            withTimeout(5_000L) {
                secondValidation.await()
            }
            firstRefresh.release.complete(Unit)

            val staleResult = withTimeout(5_000L) { firstClaim.await() }
            val winningResult = withTimeout(5_000L) { secondClaim.await() }
            assertTrue(staleResult.isFailure)
            assertTrue(winningResult.isSuccess)
            assertEquals(restored.playlistUrl, winningResult.getOrThrow().playlistUrl)

            val claimed = requireNotNull(fixture.database.providerDao().getAccount(restored.id))
            assertTrue(WINNING_PRINCIPAL.owns(claimed))
            assertFalse(claimed.requiresReauthentication)
            assertEquals(
                WINNING_CLAIM_TITLE,
                fixture.database.playlistDao().get(restored.playlistUrl)?.title,
            )
            assertEquals(
                fixture.transport.tokenFor(WINNING_PRINCIPAL),
                fixture.persistedPrimaryCredential(restored.id),
            )
        }
    }

    private suspend fun withFixture(block: suspend (TestFixture) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val credentialVault = TestCredentialVault()
        val principalRegistry = ActiveExtensionPrincipalRegistry().apply {
            activate(OLD_PRINCIPAL)
        }
        val brokerScopeStore = ProviderBrokerScopeStore(
            credentialVault = credentialVault,
            principalRegistry = principalRegistry,
        )
        val transport = TestExternalProviderTransport(
            brokerScopeStore = brokerScopeStore,
            initialPrincipal = OLD_PRINCIPAL,
        )
        val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
        val registration = runtime.register(transport)
        assertTrue(registration is ExtensionRegistrationResult.Registered)
        val registered = registration as ExtensionRegistrationResult.Registered
        runtime.recordTransportHealth(
            transport.manifest.id,
            checkNotNull(registered.registrationToken),
            ExtensionTransportHealth.HEALTHY,
        )
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
            providerBrokerScopeStore = brokerScopeStore,
            lifecycleCoordinator = ProviderLifecycleCoordinator(),
        )
        try {
            block(
                TestFixture(
                    database = database,
                    repository = repository,
                    credentialVault = credentialVault,
                    principalRegistry = principalRegistry,
                    transport = transport,
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
        val principalRegistry: ActiveExtensionPrincipalRegistry,
        val transport: TestExternalProviderTransport,
    ) {
        suspend fun createRestoredAccount(): ProviderAccount {
            val subscription = subscribe(title = ORIGINAL_TITLE)
            val original = requireNotNull(
                database.providerDao().getAccountByPlaylistUrl(subscription.playlistUrl)
            )
            assertTrue(OLD_PRINCIPAL.owns(original))
            database.providerDao().invalidateCredential(original.id)
            return requireNotNull(database.providerDao().getAccount(original.id))
                .also { restored ->
                    assertTrue(restored.requiresReauthentication)
                    assertNull(database.providerDao().getCredential(restored.id))
                }
        }

        suspend fun claim(
            playlistUrl: String,
            title: String,
        ): ProviderSubscriptionResult = subscribe(
            title = title,
            reauthenticationPlaylistUrl = playlistUrl,
        )

        suspend fun persistedPrimaryCredential(accountId: String): String {
            val encrypted = requireNotNull(database.providerDao().getCredential(accountId))
            val material = requireNotNull(credentialVault.decrypt(encrypted))
            return ProviderCredentialMaterial.decode(material).primaryCredential
        }

        fun switchPrincipal(
            oldPrincipal: ExtensionPrincipal,
            newPrincipal: ExtensionPrincipal,
        ) {
            assertEquals(
                oldPrincipal,
                principalRegistry.deactivate(
                    extensionId = oldPrincipal.extensionId,
                    packageName = oldPrincipal.packageName,
                    serviceName = oldPrincipal.serviceName,
                ),
            )
            principalRegistry.activate(newPrincipal)
            transport.invocationPrincipal = newPrincipal
        }

        private suspend fun subscribe(
            title: String,
            reauthenticationPlaylistUrl: String? = null,
        ): ProviderSubscriptionResult = repository.subscribe(
            ProviderSubscriptionRequest(
                title = title,
                providerId = EXTENSION_ID,
                providerKind = PROVIDER_KIND,
                settingValues = mapOf(
                    SubscriptionProviderSettingKeys.BaseUrl to BASE_URL,
                    SubscriptionProviderSettingKeys.Username to USERNAME,
                ),
                credentialHandles = mapOf(
                    SubscriptionProviderSettingKeys.Password to
                        repository.stageCredential(SUBMITTED_PASSWORD),
                ),
                reauthenticationPlaylistUrl = reauthenticationPlaylistUrl,
            )
        )
    }

    private class TestExternalProviderTransport(
        private val brokerScopeStore: ProviderBrokerScopeStore,
        initialPrincipal: ExtensionPrincipal,
    ) : ExtensionTransport {
        override val manifest: ExtensionManifest = MANIFEST

        @Volatile
        var invocationPrincipal: ExtensionPrincipal = initialPrincipal

        @Volatile
        var remoteServerIdentity: String = REMOTE_SERVER_ID

        @Volatile
        var remoteUserIdentity: String = REMOTE_USER_ID

        private val signalLock = Any()
        private var refreshGate: RefreshGate? = null
        private var validationSignal: CompletableDeferred<Unit>? = null

        fun blockNextRefresh(): RefreshGate = synchronized(signalLock) {
            check(refreshGate == null)
            RefreshGate().also { refreshGate = it }
        }

        fun signalNextValidation(): CompletableDeferred<Unit> = synchronized(signalLock) {
            check(validationSignal == null)
            CompletableDeferred<Unit>().also { validationSignal = it }
        }

        fun tokenFor(principal: ExtensionPrincipal): String =
            "captured-token:${principal.packageName}"

        override suspend fun invoke(
            request: SerializedExtensionEnvelope,
        ): SerializedExtensionResult = when (request.hook) {
            SubscriptionHookSpecs.Discover.hook -> request.success(
                JSON.encodeToJsonElement(
                    SubscriptionProviderDiscoverResult.serializer(),
                    SubscriptionProviderDiscoverResult(provider = PROVIDER_DESCRIPTOR),
                )
            )

            SubscriptionHookSpecs.Validate.hook -> {
                val principal = invocationPrincipal
                val receipt = brokerScopeStore.recordAuthentication(
                    scope = requireNotNull(request.brokerScope),
                    principal = principal,
                    hook = request.hook,
                    primaryCredential = tokenFor(principal),
                    opaqueContexts = mapOf(
                        ProviderAuthenticationContextKeys.ServerId to remoteServerIdentity,
                        ProviderAuthenticationContextKeys.UserId to remoteUserIdentity,
                    ),
                )
                synchronized(signalLock) {
                    validationSignal.also { validationSignal = null }
                }?.complete(Unit)
                request.success(
                    JSON.encodeToJsonElement(
                        SubscriptionProviderValidateResult.serializer(),
                        SubscriptionProviderValidateResult(
                            evidence = ProviderValidationEvidence.HostBrokerReceipt(receipt)
                        ),
                    )
                )
            }

            SubscriptionHookSpecs.Refresh.hook -> {
                val refresh = JSON.decodeFromJsonElement(
                    SubscriptionContentRefreshRequest.serializer(),
                    request.payload,
                )
                synchronized(signalLock) {
                    refreshGate.also { refreshGate = null }
                }?.let { gate ->
                    gate.entered.complete(Unit)
                    gate.release.await()
                }
                request.success(
                    JSON.encodeToJsonElement(
                        SubscriptionContentRefreshResult.serializer(),
                        SubscriptionContentRefreshResult(
                            source = SubscriptionSourceDescriptor(
                                remoteId = refresh.account.serverId,
                                providerKind = refresh.account.providerKind,
                            ),
                            channels = emptyList(),
                        ),
                    )
                )
            }

            else -> error("Unexpected Hook ${request.hook}")
        }

        override suspend fun cancel(invocationId: InvocationId) = Unit

        override suspend fun health(): ExtensionTransportHealth =
            ExtensionTransportHealth.HEALTHY

        private fun SerializedExtensionEnvelope.success(
            payload: kotlinx.serialization.json.JsonElement,
        ) = SerializedExtensionResult(
            invocationId = invocationId,
            extensionId = extensionId,
            hook = hook,
            schemaVersion = schemaVersion,
            payload = payload,
        )
    }

    private class TestCredentialVault : CredentialVault {
        private val transientSecrets = linkedMapOf<String, String>()
        private val transientSequence = AtomicInteger()
        private val persistentSequence = AtomicInteger()

        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ) = ProviderCredentialEntity(
            accountId = accountId,
            credentialHandle = credentialHandle
                ?: "persistent:${persistentSequence.incrementAndGet()}:$accountId",
            ciphertext = secret,
            nonce = "test-nonce",
            keyVersion = 1,
        )

        override fun decrypt(credential: ProviderCredentialEntity): String = credential.ciphertext

        @Synchronized
        override fun stage(secret: String): CredentialHandle {
            val handle = CredentialHandle("transient:${transientSequence.incrementAndGet()}")
            transientSecrets[handle.value] = secret
            return handle
        }

        @Synchronized
        override fun consume(handle: CredentialHandle): String? =
            transientSecrets.remove(handle.value)
    }

    private class RefreshGate(
        val entered: CompletableDeferred<Unit> = CompletableDeferred(),
        val release: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private data object NoOpExtensionContributionScheduler : ExtensionContributionScheduler {
        override suspend fun enqueue(playlistUrl: String) = Unit

        override suspend fun cancel(playlistUrl: String) = Unit
    }

    private suspend fun captureFailure(block: suspend () -> Unit): Throwable {
        try {
            block()
        } catch (failure: Throwable) {
            return failure
        }
        throw AssertionError("Expected operation to fail")
    }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
        val EXTENSION_ID = ExtensionId("com.m3u.test.provider.owner-claim")
        val OTHER_PROVIDER_ID = ExtensionId("com.m3u.test.provider.other")
        val PROVIDER_KIND = ProviderKind("test")
        const val BASE_URL = "https://media.example.test"
        const val USERNAME = "restored-user"
        const val SUBMITTED_PASSWORD = "password"
        const val REMOTE_SERVER_ID = "server-a"
        const val DIFFERENT_REMOTE_SERVER_ID = "server-b"
        const val REMOTE_USER_ID = "user-a"
        const val ORIGINAL_TITLE = "Restored provider"
        const val CLAIMED_TITLE = "Claimed provider"
        const val FIRST_CLAIM_TITLE = "Stale claimant"
        const val WINNING_CLAIM_TITLE = "Current claimant"

        val OLD_PRINCIPAL = principal(
            packageName = "com.example.provider.old",
            serviceName = "com.example.provider.old.Service",
            certificate = "11".repeat(32),
            uid = 10_001,
        )
        val CLAIMANT_PRINCIPAL = principal(
            packageName = "com.example.provider.claimant",
            serviceName = "com.example.provider.claimant.Service",
            certificate = "22".repeat(32),
            uid = 10_002,
        )
        val WINNING_PRINCIPAL = principal(
            packageName = "com.example.provider.winner",
            serviceName = "com.example.provider.winner.Service",
            certificate = "33".repeat(32),
            uid = 10_003,
        )

        val PROVIDER_DESCRIPTOR = SubscriptionProviderDescriptor(
            providerId = EXTENSION_ID,
            displayName = "Owner claim provider",
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

        val MANIFEST = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Owner claim provider",
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
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.Network,
                    reason = "Connect to the selected provider",
                ),
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.CredentialRead,
                    reason = "Use the provider session",
                ),
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.CredentialWrite,
                    reason = "Save the provider session",
                ),
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.SubscriptionRead,
                    reason = "Refresh provider content",
                ),
            ),
        )

        fun principal(
            packageName: String,
            serviceName: String,
            certificate: String,
            uid: Int,
        ) = ExtensionPrincipal(
            extensionId = EXTENSION_ID,
            packageName = packageName,
            serviceName = serviceName,
            certificateSha256 = certificate,
            uid = uid,
        )
    }
}
