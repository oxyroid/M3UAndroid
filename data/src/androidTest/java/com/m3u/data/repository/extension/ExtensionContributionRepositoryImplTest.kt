package com.m3u.data.repository.extension

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.extension.api.Capability
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.ChannelMetadataSnapshot
import com.m3u.extension.api.EpgRefreshRequest
import com.m3u.extension.api.EpgRefreshResult
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionNetworkOrigin
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.MetadataEnrichmentRequest
import com.m3u.extension.api.MetadataEnrichmentResult
import com.m3u.extension.api.SearchProviderItem
import com.m3u.extension.api.SearchProviderRequest
import com.m3u.extension.api.SearchProviderResult
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.BrokerScopeHandle
import com.m3u.extension.runtime.ExtensionBrokerScopeLease
import com.m3u.extension.runtime.ExtensionBrokerScopeProvider
import com.m3u.extension.runtime.ExtensionBrokerScopeRequest
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.runtime.InvocationPolicy
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionContributionRepositoryImplTest {
    private lateinit var database: M3UDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            M3UDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun accountlessSearchInvokesOnceWithoutProviderSecretsAndUsesOnlyHookCapabilities() =
        runBlocking {
            val target = insertProviderChannel(
                extensionId = SEARCH_TARGET_EXTENSION_ID,
                accountId = "search-target-account",
                remoteId = "search-target-channel",
            )
            lateinit var scopeRequest: ExtensionBrokerScopeRequest
            lateinit var transportRequest: SerializedExtensionEnvelope
            var scopeClosed = false
            val scopeHandle = BrokerScopeHandle("search-hook-scope")
            val manifest = searchManifest(
                extensionId = ACCOUNTLESS_SEARCH_EXTENSION_ID,
                requiredCapabilities = setOf(
                    ExtensionCapabilityIds.SearchRead,
                    ExtensionCapabilityIds.Network,
                ),
                additionalCapabilities = setOf(ExtensionCapabilityIds.CredentialRead),
            )
            val runtime = ExtensionRuntime(
                hostApiVersion = ExtensionApiVersions.Current,
                brokerScopeProvider = ExtensionBrokerScopeProvider { request ->
                    scopeRequest = request
                    object : ExtensionBrokerScopeLease {
                        override val handle = scopeHandle

                        override fun close() {
                            scopeClosed = true
                        }
                    }
                },
            )
            val transport = object : ExtensionTransport {
                override val manifest = manifest

                override suspend fun invoke(
                    request: SerializedExtensionEnvelope,
                ): SerializedExtensionResult {
                    transportRequest = request
                    return request.success(
                        SearchProviderResult(
                            items = listOf(
                                SearchProviderItem(
                                    accountId = target.account.id,
                                    remoteId = target.channel.relationId.orEmpty(),
                                )
                            )
                        )
                    )
                }

                override suspend fun cancel(invocationId: InvocationId) = Unit

                override suspend fun health(): ExtensionTransportHealth =
                    ExtensionTransportHealth.HEALTHY
            }
            val registration = runtime.register(transport)
            assertTrue(registration is ExtensionRegistrationResult.Registered)
            val registered = registration as ExtensionRegistrationResult.Registered
            runtime.recordTransportHealth(
                manifest.id,
                checkNotNull(registered.registrationToken),
                ExtensionTransportHealth.HEALTHY,
            )

            val contributions = repository(runtime).search("  news  ", limit = 10)

            val request = scopeRequest.payload as SearchProviderRequest
            assertEquals("news", request.query)
            assertNull(request.account)
            assertNull(request.credential)
            assertEquals(
                setOf(
                    ExtensionCapabilityIds.SearchRead,
                    ExtensionCapabilityIds.Network,
                ),
                scopeRequest.grantedCapabilities,
            )
            assertFalse(
                ExtensionCapabilityIds.CredentialRead in scopeRequest.grantedCapabilities
            )
            assertEquals(scopeHandle, transportRequest.brokerScope)
            assertTrue(scopeClosed)
            assertEquals(listOf(target.channel.id), contributions.map { it.channel.id })
        }

    @Test
    fun accountBoundSearchInvokesEachOwnedAccountWithoutAnAccountlessCall() = runBlocking {
        val first = insertProviderChannel(
            extensionId = ACCOUNT_BOUND_SEARCH_EXTENSION_ID,
            accountId = "search-account-a",
            remoteId = "channel-a",
        )
        val second = insertProviderChannel(
            extensionId = ACCOUNT_BOUND_SEARCH_EXTENSION_ID,
            accountId = "search-account-b",
            remoteId = "channel-b",
        )
        val requests = mutableListOf<SearchProviderRequest>()
        val contexts = mutableListOf<ExtensionCallContext>()
        val runtime = runtimeWith(
            searchExtension(ACCOUNT_BOUND_SEARCH_EXTENSION_ID) { context, request ->
                contexts += context
                requests += request
                val accountId = checkNotNull(request.account).accountId
                HookResult.Success(
                    SearchProviderResult(
                        listOf(
                            SearchProviderItem(
                                accountId = accountId,
                                remoteId = if (accountId == first.account.id) {
                                    first.channel.relationId.orEmpty()
                                } else {
                                    second.channel.relationId.orEmpty()
                                },
                            )
                        )
                    )
                )
            }
        )

        val contributions = repository(runtime).search("sports", limit = 10)

        assertEquals(
            setOf(first.account.id, second.account.id),
            requests.mapTo(mutableSetOf()) { request ->
                checkNotNull(request.account).accountId
            },
        )
        assertTrue(requests.all { request -> request.credential != null })
        assertEquals(2, requests.size)
        assertTrue(
            contexts.all { context ->
                context.grantedCapabilities == setOf(ExtensionCapabilityIds.SearchRead)
            }
        )
        assertEquals(
            setOf(first.channel.id, second.channel.id),
            contributions.mapTo(mutableSetOf()) { contribution -> contribution.channel.id },
        )
    }

    @Test
    fun permanentEpgHookFailureDoesNotProduceSuccessfulBatch() = runBlocking {
        val runtime = runtimeWith(
            epgExtension(FAILURE_EXTENSION_ID) {
                HookResult.Failure(
                    ExtensionError(
                        code = ExtensionErrorCodes.InvocationFailed,
                        message = "Expected test failure",
                        recoverable = false,
                    )
                )
            },
        )

        val contributions = repository(runtime).refreshEpg(
            channelReferences = listOf(CHANNEL_REFERENCE),
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertTrue(contributions.isEmpty())
    }

    @Test
    fun recoverableEpgHookFailureRequestsAWorkerRetry() {
        val runtime = runtimeWith(
            epgExtension(FAILURE_EXTENSION_ID) {
                HookResult.Failure(
                    ExtensionError(
                        code = ExtensionErrorCodes.InvocationFailed,
                        message = "Expected recoverable test failure",
                        recoverable = true,
                    )
                )
            }
        )

        assertThrows(RecoverableExtensionContributionException::class.java) {
            runBlocking {
                repository(runtime).refreshEpg(
                    channelReferences = listOf(CHANNEL_REFERENCE),
                    fromEpochMillis = WINDOW_START,
                    toEpochMillis = WINDOW_END,
                )
            }
        }
    }

    @Test
    fun unexpectedEpgHookExceptionRequestsAWorkerRetry() {
        val runtime = runtimeWith(
            epgExtension(EXCEPTION_EXTENSION_ID) {
                throw IllegalStateException("Expected test exception")
            }
        )

        assertThrows(RecoverableExtensionContributionException::class.java) {
            runBlocking {
                repository(runtime).refreshEpg(
                    channelReferences = listOf(CHANNEL_REFERENCE),
                    fromEpochMillis = WINDOW_START,
                    toEpochMillis = WINDOW_END,
                )
            }
        }
    }

    @Test
    fun metadataContributionsKeepEverySuccessfulOwnerForTheSameChannel() = runBlocking {
        val firstExtension = ExtensionId("com.m3u.test.metadata.first")
        val secondExtension = ExtensionId("com.m3u.test.metadata.second")
        val runtime = runtimeWith(
            metadataExtension(firstExtension) { request ->
                HookResult.Success(
                    MetadataEnrichmentResult(
                        listOf(
                            ChannelMetadataPatch(
                                stableReference = request.channels.single().stableReference,
                                title = "First title",
                            )
                        )
                    )
                )
            },
            metadataExtension(secondExtension) { request ->
                HookResult.Success(
                    MetadataEnrichmentResult(
                        listOf(
                            ChannelMetadataPatch(
                                stableReference = request.channels.single().stableReference,
                                title = "Second title",
                            )
                        )
                    )
                )
            },
        )

        val contributions = repository(runtime).enrichChannels(
            listOf(ChannelMetadataSnapshot(CHANNEL_REFERENCE, "Base title", "Base category"))
        )

        assertEquals(setOf(firstExtension, secondExtension), contributions.mapTo(mutableSetOf()) {
            contribution -> contribution.extensionId
        })
        assertTrue(contributions.all { contribution -> contribution.patches.size == 1 })
    }

    @Test
    fun metadataEmptySuccessKeepsOwnerBatchWhileFailureDoesNotReplaceOwner() = runBlocking {
        val successfulExtension = ExtensionId("com.m3u.test.metadata.empty")
        val failedExtension = ExtensionId("com.m3u.test.metadata.failure")
        val runtime = runtimeWith(
            metadataExtension(successfulExtension) {
                HookResult.Success(MetadataEnrichmentResult(emptyList()))
            },
            metadataExtension(failedExtension) {
                HookResult.Failure(
                    ExtensionError(
                        code = ExtensionErrorCodes.InvocationFailed,
                        message = "Expected test failure",
                        recoverable = false,
                    )
                )
            },
        )

        val contributions = repository(runtime).enrichChannels(
            listOf(ChannelMetadataSnapshot(CHANNEL_REFERENCE, "Base title", "Base category"))
        )

        assertEquals(1, contributions.size)
        assertEquals(successfulExtension, contributions.single().extensionId)
        assertTrue(contributions.single().patches.isEmpty())
    }

    @Test
    fun successfulEpgHookWithNoProgrammesKeepsItsExtensionBatch() = runBlocking {
        val runtime = runtimeWith(
            epgExtension(EMPTY_SUCCESS_EXTENSION_ID) {
                HookResult.Success(EpgRefreshResult(programmes = emptyList()))
            }
        )

        val contributions = repository(runtime).refreshEpg(
            channelReferences = listOf(CHANNEL_REFERENCE),
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertEquals(1, contributions.size)
        assertEquals(EMPTY_SUCCESS_EXTENSION_ID, contributions.single().extensionId)
        assertTrue(contributions.single().programmes.isEmpty())
    }

    @Test
    fun epgHookCancellationPropagatesToTheCaller() {
        val runtime = runtimeWith(
            epgExtension(CANCELLED_EXTENSION_ID) {
                throw CancellationException(CANCELLATION_MESSAGE)
            }
        )

        val thrown = assertThrows(CancellationException::class.java) {
            runBlocking {
                repository(runtime).refreshEpg(
                    channelReferences = listOf(CHANNEL_REFERENCE),
                    fromEpochMillis = WINDOW_START,
                    toEpochMillis = WINDOW_END,
                )
            }
        }

        assertEquals(CANCELLATION_MESSAGE, thrown.message)
    }

    @Test
    fun tooManyChannelReferencesDoNotInvokeExtensionsOrProduceSuccessfulBatches() = runBlocking {
        var invocationCount = 0
        val runtime = runtimeWith(
            epgExtension(CHANNEL_LIMIT_EXTENSION_ID) {
                invocationCount += 1
                HookResult.Success(EpgRefreshResult(programmes = emptyList()))
            }
        )

        val contributions = repository(runtime).refreshEpg(
            channelReferences = List(MAX_CHANNELS_PER_ENRICHMENT + 1) { index ->
                "channel-$index"
            },
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertTrue(contributions.isEmpty())
        assertEquals(0, invocationCount)
    }

    @Test
    fun oversizedEpgBatchDoesNotProduceSuccessfulExtensionBatch() = runBlocking {
        var invocationCount = 0
        val runtime = runtimeWith(
            epgExtension(BATCH_LIMIT_EXTENSION_ID) { request ->
                invocationCount += 1
                HookResult.Success(
                    EpgRefreshResult(
                        programmes = List(MAX_PROGRAMMES_PER_BATCH + 1) {
                            validProgramme(request.sourceIds.single())
                        },
                    )
                )
            },
            invocationPolicy = LARGE_PAYLOAD_INVOCATION_POLICY,
        )

        val contributions = repository(runtime).refreshEpg(
            channelReferences = listOf(CHANNEL_REFERENCE),
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertTrue(contributions.isEmpty())
        assertEquals(1, invocationCount)
    }

    @Test
    fun cumulativeEpgProgrammeLimitDoesNotProduceSuccessfulExtensionBatch() = runBlocking {
        var invocationCount = 0
        val runtime = runtimeWith(
            epgExtension(EXTENSION_LIMIT_EXTENSION_ID) { request ->
                invocationCount += 1
                val programmeCount = if (invocationCount <= FULL_EPG_BATCH_COUNT) {
                    MAX_PROGRAMMES_PER_BATCH
                } else {
                    1
                }
                HookResult.Success(
                    EpgRefreshResult(
                        programmes = List(programmeCount) {
                            validProgramme(request.sourceIds.first())
                        },
                    )
                )
            },
            invocationPolicy = LARGE_PAYLOAD_INVOCATION_POLICY,
        )

        val contributions = repository(runtime).refreshEpg(
            channelReferences = List(CHANNEL_ENRICHMENT_BATCH_SIZE * FULL_EPG_BATCH_COUNT + 1) {
                index -> "channel-$index"
            },
            fromEpochMillis = WINDOW_START,
            toEpochMillis = WINDOW_END,
        )

        assertTrue(contributions.isEmpty())
        assertEquals(FULL_EPG_BATCH_COUNT + 1, invocationCount)
    }

    private suspend fun insertProviderChannel(
        extensionId: ExtensionId,
        accountId: String,
        remoteId: String,
    ): SeededProviderChannel {
        val playlistUrl = "provider://$accountId"
        database.playlistDao().insertOrReplace(
            Playlist(
                title = accountId,
                url = playlistUrl,
                source = DataSource.Provider,
            )
        )
        val account = ProviderAccount(
            id = accountId,
            providerId = extensionId.value,
            providerKind = "test",
            baseUrl = "https://media.example.test",
            serverId = "server-$accountId",
            serverName = "Test server",
            serverVersion = "1.0",
            userId = "user-$accountId",
            username = "viewer",
            playlistUrl = playlistUrl,
        )
        database.providerDao().insertOrReplace(account)
        database.providerDao().insertOrReplace(
            ProviderCredentialEntity(
                accountId = accountId,
                credentialHandle = "credential-$accountId",
                ciphertext = "ciphertext",
                nonce = "nonce",
                keyVersion = 1,
            )
        )
        val channel = Channel(
            url = Channel.URL_DYNAMIC,
            category = "Test",
            title = remoteId,
            playlistUrl = playlistUrl,
            relationId = remoteId,
        )
        val channelId = database.channelDao().insertOrReplace(channel).toInt()
        return SeededProviderChannel(account, channel.copy(id = channelId))
    }

    private fun repository(runtime: ExtensionRuntime) = ExtensionContributionRepositoryImpl(
        runtime = runtime,
        providerDao = database.providerDao(),
        channelDao = database.channelDao(),
    )

    private fun runtimeWith(
        vararg entrypoints: ExtensionEntrypoint,
        invocationPolicy: InvocationPolicy = InvocationPolicy(),
    ): ExtensionRuntime =
        ExtensionRuntime(
            hostApiVersion = ExtensionApiVersions.Current,
            invocationPolicy = invocationPolicy,
        ).also { runtime ->
            entrypoints.forEach { entrypoint ->
                assertTrue(runtime.register(entrypoint) is ExtensionRegistrationResult.Registered)
            }
        }

    private fun searchExtension(
        extensionId: ExtensionId,
        invoke: suspend (
            ExtensionCallContext,
            SearchProviderRequest,
        ) -> HookResult<SearchProviderResult>,
    ): ExtensionEntrypoint = object : ExtensionEntrypoint {
        override val manifest = searchManifest(extensionId)
        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<SearchProviderRequest, SearchProviderResult> {
                override val spec = HostHookSpecs.SearchProvider

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: SearchProviderRequest,
                ): HookResult<SearchProviderResult> = invoke(context, request)
            }
        )
    }

    private fun searchManifest(
        extensionId: ExtensionId,
        requiredCapabilities: Set<Capability> =
            setOf(ExtensionCapabilityIds.SearchRead),
        additionalCapabilities: Set<Capability> = emptySet(),
    ): ExtensionManifest {
        val requestedCapabilities = requiredCapabilities + additionalCapabilities
        return ExtensionManifest(
            id = extensionId,
            displayName = "Search repository test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.SearchProvider.hook,
                    schemaVersion = HostHookSpecs.SearchProvider.schemaVersion,
                    requiredCapabilities = requiredCapabilities,
                )
            ),
            capabilities = requestedCapabilities.mapTo(mutableSetOf()) { capability ->
                ExtensionCapabilityRequest(
                    capability = capability,
                    reason = "Exercise search repository capability scoping",
                )
            },
            networkOrigins = if (ExtensionCapabilityIds.Network in requestedCapabilities) {
                setOf(ExtensionNetworkOrigin("https://search.example.test"))
            } else {
                emptySet()
            },
        )
    }

    private fun SerializedExtensionEnvelope.success(
        payload: SearchProviderResult,
    ) = SerializedExtensionResult(
        invocationId = invocationId,
        extensionId = extensionId,
        hook = hook,
        schemaVersion = schemaVersion,
        payload = JSON.encodeToJsonElement(SearchProviderResult.serializer(), payload),
    )

    private fun validProgramme(channelReference: String) = ExtensionProgramme(
        channelReference = channelReference,
        title = "Programme",
        startEpochMillis = WINDOW_START,
        endEpochMillis = WINDOW_END,
    )

    private fun epgExtension(
        extensionId: ExtensionId,
        invoke: suspend (EpgRefreshRequest) -> HookResult<EpgRefreshResult>,
    ): ExtensionEntrypoint = object : ExtensionEntrypoint {
        override val manifest = ExtensionManifest(
            id = extensionId,
            displayName = "EPG repository test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.EpgRefresh.hook,
                    schemaVersion = HostHookSpecs.EpgRefresh.schemaVersion,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.EpgRead),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.EpgRead,
                    reason = "Contribute test EPG data",
                )
            ),
        )
        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<EpgRefreshRequest, EpgRefreshResult> {
                override val spec = HostHookSpecs.EpgRefresh

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: EpgRefreshRequest,
                ): HookResult<EpgRefreshResult> = invoke(request)
            }
        )
    }

    private fun metadataExtension(
        extensionId: ExtensionId,
        invoke: suspend (MetadataEnrichmentRequest) -> HookResult<MetadataEnrichmentResult>,
    ): ExtensionEntrypoint = object : ExtensionEntrypoint {
        override val manifest = ExtensionManifest(
            id = extensionId,
            displayName = "Metadata repository test",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.MetadataEnrichment.hook,
                    schemaVersion = HostHookSpecs.MetadataEnrichment.schemaVersion,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.MetadataWrite),
                )
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.MetadataWrite,
                    reason = "Contribute test metadata",
                )
            ),
        )
        override val handlers: Collection<ExtensionHandler<*, *>> = listOf(
            object : ExtensionHandler<MetadataEnrichmentRequest, MetadataEnrichmentResult> {
                override val spec = HostHookSpecs.MetadataEnrichment

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: MetadataEnrichmentRequest,
                ): HookResult<MetadataEnrichmentResult> = invoke(request)
            }
        )
    }

    private data class SeededProviderChannel(
        val account: ProviderAccount,
        val channel: Channel,
    )

    private companion object {
        val JSON = Json
        val ACCOUNTLESS_SEARCH_EXTENSION_ID =
            ExtensionId("com.m3u.test.search.accountless")
        val ACCOUNT_BOUND_SEARCH_EXTENSION_ID =
            ExtensionId("com.m3u.test.search.account-bound")
        val SEARCH_TARGET_EXTENSION_ID =
            ExtensionId("com.m3u.test.search.target")
        val FAILURE_EXTENSION_ID = ExtensionId("com.m3u.test.epg.failure")
        val EXCEPTION_EXTENSION_ID = ExtensionId("com.m3u.test.epg.exception")
        val EMPTY_SUCCESS_EXTENSION_ID = ExtensionId("com.m3u.test.epg.empty")
        val CANCELLED_EXTENSION_ID = ExtensionId("com.m3u.test.epg.cancelled")
        val CHANNEL_LIMIT_EXTENSION_ID = ExtensionId("com.m3u.test.epg.channel-limit")
        val BATCH_LIMIT_EXTENSION_ID = ExtensionId("com.m3u.test.epg.batch-limit")
        val EXTENSION_LIMIT_EXTENSION_ID = ExtensionId("com.m3u.test.epg.extension-limit")
        val LARGE_PAYLOAD_INVOCATION_POLICY = InvocationPolicy(maxPayloadBytes = 16 * 1_048_576)
        const val CHANNEL_REFERENCE = "channel-1"
        const val WINDOW_START = 1_000L
        const val WINDOW_END = 2_000L
        const val CANCELLATION_MESSAGE = "Expected test cancellation"
        const val CHANNEL_ENRICHMENT_BATCH_SIZE = 200
        const val MAX_CHANNELS_PER_ENRICHMENT = 5_000
        const val MAX_PROGRAMMES_PER_BATCH = 10_000
        const val MAX_PROGRAMMES_PER_EXTENSION = 50_000
        const val FULL_EPG_BATCH_COUNT = MAX_PROGRAMMES_PER_EXTENSION / MAX_PROGRAMMES_PER_BATCH
    }
}
