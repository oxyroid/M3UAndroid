package com.m3u.data.repository.provider

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.AndroidKeystoreCredentialVault
import com.m3u.data.extension.security.ProviderBrokerScopeStore
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionHookSpecs
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverRequest
import com.m3u.extension.api.subscription.SubscriptionProviderDiscoverResult
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionProviderDiscoveryTest {
    @Test
    fun partialFailureKeepsValidProviders() = withRepository { repository, runtime, _ ->
        register(runtime, providerEntrypoint(VALID_PROVIDER_ID, validDescriptor(VALID_PROVIDER_ID)))
        register(
            runtime,
            providerEntrypoint(
                id = INVALID_PROVIDER_ID,
                descriptor = validDescriptor(ExtensionId("com.example.wrong-provider")),
            ),
        )

        val providers = repository.discoverProviders()

        assertEquals(1, providers.size)
        assertEquals(VALID_PROVIDER_ID, providers.single().descriptor.providerId)
        assertEquals(
            SubscriptionProviderExecutionKind.BUILT_IN,
            providers.single().executionKind,
        )
    }

    @Test
    fun everyFailedOrInvalidProviderProducesStableAggregateFailure() =
        withRepository { repository, runtime, _ ->
            register(
                runtime,
                providerEntrypoint(
                    id = INVALID_PROVIDER_ID,
                    descriptor = validDescriptor(ExtensionId("com.example.wrong-provider")),
                ),
            )
            register(runtime, failingProviderEntrypoint(FAILED_PROVIDER_ID))

            val failure = runCatching { repository.discoverProviders() }.exceptionOrNull()

            assertTrue(failure is ProviderDiscoveryException)
            failure as ProviderDiscoveryException
            assertEquals(2, failure.failureCount)
            assertEquals("provider.discovery_failed", failure.code)
            assertFalse(failure.message.orEmpty().contains("private provider detail"))
        }

    @Test
    fun noRegisteredProviderIsAnEmptySuccessfulDiscovery() =
        withRepository { repository, _, _ ->
            assertTrue(repository.discoverProviders().isEmpty())
        }

    @Test
    fun accountSummaryContainsReauthenticationFieldsButNoCredential() =
        withRepository { repository, _, database ->
            val playlistUrl = "m3u-provider://account/summary/live"
            database.playlistDao().insertOrReplace(
                Playlist(
                    title = "Living room",
                    url = playlistUrl,
                    source = DataSource.Provider,
                )
            )
            database.providerDao().insertOrReplace(
                ProviderAccount(
                    id = "summary",
                    providerId = VALID_PROVIDER_ID.value,
                    providerKind = PROVIDER_KIND.value,
                    baseUrl = "https://media.example.test",
                    serverId = "server-id",
                    serverName = "Home server",
                    serverVersion = "1",
                    userId = "user-id",
                    username = "viewer",
                    playlistUrl = playlistUrl,
                    requiresReauthentication = true,
                )
            )

            val summary = repository.observeAccountSummaries().first().single()

            assertEquals("Living room", summary.playlistTitle)
            assertEquals(playlistUrl, summary.playlistUrl)
            assertEquals(VALID_PROVIDER_ID, summary.providerId)
            assertEquals(PROVIDER_KIND, summary.providerKind)
            assertEquals("https://media.example.test", summary.baseUrl)
            assertEquals("viewer", summary.username)
            assertEquals("Home server", summary.serverName)
            assertTrue(summary.requiresReauthentication)
        }

    private fun withRepository(
        block: suspend (
            SubscriptionProviderRepositoryImpl,
            ExtensionRuntime,
            M3UDatabase,
        ) -> Unit,
    ) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val runtime = ExtensionRuntime(ExtensionApiVersions.Current)
            val credentialVault = AndroidKeystoreCredentialVault(
                context = context,
                keyAlias = "m3u.discovery.test.${System.nanoTime()}",
            )
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
            block(repository, runtime, database)
        } finally {
            database.close()
        }
    }

    private fun register(runtime: ExtensionRuntime, entrypoint: ExtensionEntrypoint) {
        assertTrue(runtime.register(entrypoint) is ExtensionRegistrationResult.Registered)
    }

    private fun providerEntrypoint(
        id: ExtensionId,
        descriptor: SubscriptionProviderDescriptor,
    ): ExtensionEntrypoint = entrypoint(id) { _, _ ->
        HookResult.Success(SubscriptionProviderDiscoverResult(listOf(descriptor)))
    }

    private fun failingProviderEntrypoint(id: ExtensionId): ExtensionEntrypoint = entrypoint(id) { _, _ ->
        HookResult.Failure(
            ExtensionError(
                code = ExtensionErrorCodes.InvocationFailed,
                message = "private provider detail",
                recoverable = true,
            )
        )
    }

    private fun entrypoint(
        id: ExtensionId,
        handlerBlock: suspend (
            ExtensionCallContext,
            SubscriptionProviderDiscoverRequest,
        ) -> HookResult<SubscriptionProviderDiscoverResult>,
    ): ExtensionEntrypoint = object : ExtensionEntrypoint {
        override val manifest = ExtensionManifest(
            id = id,
            displayName = id.value,
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(ExtensionApiVersions.Current, ExtensionApiVersions.Current),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = SubscriptionHookSpecs.Discover.hook,
                    schemaVersion = SubscriptionHookSpecs.Discover.schemaVersion,
                )
            ),
            capabilities = emptySet(),
        )
        override val handlers = listOf(
            object : ExtensionHandler<
                SubscriptionProviderDiscoverRequest,
                SubscriptionProviderDiscoverResult,
                > {
                override val spec = SubscriptionHookSpecs.Discover

                override suspend fun invoke(
                    context: ExtensionCallContext,
                    request: SubscriptionProviderDiscoverRequest,
                ): HookResult<SubscriptionProviderDiscoverResult> = handlerBlock(context, request)
            }
        )
    }

    private fun validDescriptor(providerId: ExtensionId) = SubscriptionProviderDescriptor(
        providerId = providerId,
        displayName = "Provider",
        variants = listOf(SubscriptionProviderVariant(PROVIDER_KIND, "Provider")),
        settingsSchema = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = SubscriptionProviderSettingKeys.BaseUrl,
                    label = "Server",
                    type = ExtensionSettingType.TEXT,
                    required = true,
                )
            ),
        ),
    )

    private data object NoOpExtensionContributionScheduler : ExtensionContributionScheduler {
        override fun enqueue(playlistUrl: String) = Unit
    }

    private companion object {
        val VALID_PROVIDER_ID = ExtensionId("com.example.valid-provider")
        val INVALID_PROVIDER_ID = ExtensionId("com.example.invalid-provider")
        val FAILED_PROVIDER_ID = ExtensionId("com.example.failed-provider")
        val PROVIDER_KIND = ProviderKind("example")
    }
}
