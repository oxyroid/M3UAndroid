package com.m3u.data.repository.plugin

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.extension.security.ExtensionSecretStore
import com.m3u.data.extension.security.InactiveExtensionPrincipalLeaseException
import com.m3u.data.extension.security.ProviderAccountOwnerStore
import com.m3u.data.repository.extension.ExtensionSettingStore
import com.m3u.data.repository.extension.ExtensionSettingUpdateResult
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.data.repository.extension.ExtensionSettingsRepository
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.runtime.CapabilityPolicy
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.transport.android.ExtensionTrustStore
import com.m3u.extension.transport.android.InstalledExtensionService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionPluginRepositoryLifecycleTest {
    private lateinit var context: Context
    private lateinit var database: M3UDatabase
    private lateinit var trustStore: ExtensionTrustStore
    private lateinit var settingStore: ExtensionSettingStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("extension-settings", Context.MODE_PRIVATE).edit().clear().commit()
        context.settings.edit { preferences ->
            preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
        }
        database = Room.inMemoryDatabaseBuilder(context, M3UDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trustStore = ExtensionTrustStore(context)
        settingStore = ExtensionSettingStore(context, NoOpSecretStore)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun disableWaitsForReconnectCommitAndWins() = runBlocking {
        trust(MANIFEST)
        val connectionStarted = CompletableDeferred<Unit>()
        val releaseConnection = CompletableDeferred<Unit>()
        lateinit var transport: FakePluginTransport
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionStarted.complete(Unit)
                releaseConnection.await()
                FakePluginTransport(MANIFEST).also { transport = it }
            }
        )

        val reconnect = async { repository.restoreEnabled() }
        connectionStarted.await()
        val disable = async { repository.disable(EXTENSION_ID.value) }
        yield()

        assertNull(withTimeoutOrNull(100) { disable.await() })
        releaseConnection.complete(Unit)

        assertEquals(1, reconnect.await())
        assertTrue(disable.await())
        assertFalse(trustStore.isEnabled(SERVICE))
        assertTrue(runtimeExtensions(repository).isEmpty())
        assertTrue(transport.closed)
    }

    @Test
    fun disableWaitsForStartedProviderCommitAndRejectsEveryQueuedLease() = runBlocking {
        val principalRegistry = ActiveExtensionPrincipalRegistry()
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            activePrincipalRegistry = principalRegistry,
        )
        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Enabled
        )
        val startedLease = checkNotNull(principalRegistry.captureLease(EXTENSION_ID))
        val queuedLease = checkNotNull(principalRegistry.captureLease(EXTENSION_ID))
        val persistenceStarted = CompletableDeferred<Unit>()
        val releasePersistence = CompletableDeferred<Unit>()
        val startedCommit = async {
            principalRegistry.commit(startedLease) {
                persistenceStarted.complete(Unit)
                releasePersistence.await()
                "saved"
            }
        }

        persistenceStarted.await()
        val disable = async { repository.disable(EXTENSION_ID.value) }
        yield()

        assertFalse(disable.isCompleted)
        releasePersistence.complete(Unit)

        assertEquals("saved", startedCommit.await())
        assertTrue(disable.await())
        assertTrue(
            runCatching { principalRegistry.commit(queuedLease) { Unit } }
                .exceptionOrNull() is InactiveExtensionPrincipalLeaseException
        )
    }

    @Test
    fun revokeWaitsForExplicitEnableCommitAndWins() = runBlocking {
        val connectionStarted = CompletableDeferred<Unit>()
        val releaseConnection = CompletableDeferred<Unit>()
        lateinit var transport: FakePluginTransport
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionStarted.complete(Unit)
                releaseConnection.await()
                FakePluginTransport(MANIFEST).also { transport = it }
            }
        )

        val enable = async { repository.enable(SERVICE.packageName, SERVICE.serviceName) }
        connectionStarted.await()
        val revoke = async { repository.revoke(SERVICE.packageName, SERVICE.serviceName) }
        yield()

        assertNull(withTimeoutOrNull(100) { revoke.await() })
        releaseConnection.complete(Unit)

        assertTrue(enable.await() is PluginEnableResult.Enabled)
        revoke.await()
        assertFalse(trustStore.isTrusted(SERVICE))
        assertTrue(runtimeExtensions(repository).isEmpty())
        assertTrue(transport.closed)
    }

    @Test
    fun failedRestoreIsRetriedOnTheNextRestore() = runBlocking {
        trust(MANIFEST)
        var attempts = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                attempts++
                if (attempts == 1) error("transient connection failure")
                FakePluginTransport(MANIFEST)
            }
        )

        assertEquals(0, repository.restoreEnabled())
        assertEquals(1, repository.restoreEnabled())
        assertEquals(2, attempts)
        assertEquals(listOf(EXTENSION_ID), runtimeExtensions(repository))
    }

    @Test
    fun automaticReconnectPersistsCapabilityShrinkWithoutDisabling() = runBlocking {
        trust(
            MANIFEST_WITH_REMOVED_CAPABILITY,
            grants = setOf(
                ExtensionCapabilityIds.SearchRead.id,
                ExtensionCapabilityIds.MetadataWrite.id,
            ),
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(MANIFEST_WITH_REMOVED_CAPABILITY)
            }
        )

        assertEquals(1, repository.restoreEnabled())

        assertEquals(
            setOf(ExtensionCapabilityIds.SearchRead.id),
            trustStore.grantedCapabilities(EXTENSION_ID.value),
        )
        assertTrue(trustStore.isEnabled(SERVICE))
    }

    @Test
    fun connectorCancellationIsPropagatedWithoutTrustCommit() = runBlocking {
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                throw CancellationException("caller cancelled")
            }
        )

        var propagated = false
        try {
            repository.enable(SERVICE.packageName, SERVICE.serviceName)
        } catch (_: CancellationException) {
            propagated = true
        }

        assertTrue(propagated)
        assertFalse(trustStore.isTrusted(SERVICE))
        assertTrue(runtimeExtensions(repository).isEmpty())
    }

    @Test
    fun trustedExtensionIdCannotBeClaimedByAnotherService() = runBlocking {
        trust(MANIFEST)
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            services = listOf(SERVICE, OTHER_SERVICE),
        )

        val result = repository.enable(OTHER_SERVICE.packageName, OTHER_SERVICE.serviceName)

        assertTrue(result is PluginEnableResult.Rejected)
        assertFalse(trustStore.isTrusted(OTHER_SERVICE))
        assertTrue(runtimeExtensions(repository).isEmpty())
    }

    @Test
    fun incompatiblePackageIsDisabledWithoutConnecting() = runBlocking {
        trust(MANIFEST)
        var connectionAttempts = 0
        val incompatibleService = SERVICE.copy(
            incompatibilityReason = "Extension package must use the host network broker"
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempts++
                FakePluginTransport(MANIFEST)
            },
            services = listOf(incompatibleService),
        )

        assertEquals(0, repository.restoreEnabled())
        val plugin = repository.installedPlugins().single()

        assertEquals(0, connectionAttempts)
        assertFalse(trustStore.isEnabled(SERVICE))
        assertEquals(ExtensionState.INCOMPATIBLE, plugin.state)
        assertEquals(incompatibleService.incompatibilityReason, plugin.inspectionError)
    }

    @Test
    fun explicitEnableAndReauthorizeQuarantineIncompatiblePackage() = runBlocking {
        trust(MANIFEST)
        var connectionAttempts = 0
        val incompatibleService = SERVICE.copy(
            incompatibilityReason = "Extension package must use the host network broker"
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempts++
                FakePluginTransport(MANIFEST)
            },
            services = listOf(incompatibleService),
        )

        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Rejected
        )
        assertFalse(trustStore.isEnabled(SERVICE))
        trust(MANIFEST)
        assertTrue(
            repository.reauthorize(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Rejected
        )

        assertEquals(0, connectionAttempts)
        assertFalse(trustStore.isEnabled(SERVICE))
        assertTrue(runtimeExtensions(repository).isEmpty())
    }

    @Test
    fun clearDataRequiresTheInstalledServiceToBeTheSoleTrustedOwner() = runBlocking {
        trust(MANIFEST)
        settingStore.save(
            EXTENSION_ID.value,
            ExtensionSettingsSnapshot(values = mapOf("section/value" to JsonPrimitive("kept"))),
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            services = listOf(SERVICE, OTHER_SERVICE),
            extensionSettingsRepository = StoreBackedSettingsRepository(settingStore),
        )

        val untrustedResult = repository.clearData(
            OTHER_SERVICE.packageName,
            OTHER_SERVICE.serviceName,
        )
        assertTrue(untrustedResult is PluginDataClearResult.Rejected)
        assertEquals(
            JsonPrimitive("kept"),
            settingStore.snapshot(EXTENSION_ID.value).values["section/value"],
        )

        trust(MANIFEST, service = OTHER_SERVICE)
        val ambiguousResult = repository.clearData(SERVICE.packageName, SERVICE.serviceName)
        assertTrue(ambiguousResult is PluginDataClearResult.Rejected)
        assertTrue(settingStore.snapshot(EXTENSION_ID.value).values.isNotEmpty())
    }

    @Test
    fun soleTrustedOwnerCanClearItsData() = runBlocking {
        trust(MANIFEST)
        settingStore.save(
            EXTENSION_ID.value,
            ExtensionSettingsSnapshot(values = mapOf("section/value" to JsonPrimitive("clear"))),
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            extensionSettingsRepository = StoreBackedSettingsRepository(settingStore),
        )

        val result = repository.clearData(SERVICE.packageName, SERVICE.serviceName)

        assertTrue(result is PluginDataClearResult.Cleared)
        assertTrue(settingStore.snapshot(EXTENSION_ID.value).values.isEmpty())
    }

    @Test
    fun clearDataInvalidatesInFlightProviderLeaseWithoutDisablingThePrincipal() = runBlocking {
        trust(MANIFEST)
        val principalRegistry = ActiveExtensionPrincipalRegistry()
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            activePrincipalRegistry = principalRegistry,
        )
        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Enabled
        )
        val staleLease = checkNotNull(principalRegistry.captureLease(EXTENSION_ID))

        assertTrue(
            repository.clearData(SERVICE.packageName, SERVICE.serviceName) is
                PluginDataClearResult.Cleared
        )

        assertTrue(
            runCatching { principalRegistry.commit(staleLease) { Unit } }
                .exceptionOrNull() is InactiveExtensionPrincipalLeaseException
        )
        val currentLease = checkNotNull(principalRegistry.captureLease(EXTENSION_ID))
        assertEquals("current", principalRegistry.commit(currentLease) { "current" })
    }

    @Test
    fun missingTrustIsVisibleAndForgetClearsDataBeforeReleasingItsId() = runBlocking {
        trust(MANIFEST)
        settingStore.save(
            EXTENSION_ID.value,
            ExtensionSettingsSnapshot(values = mapOf("section/value" to JsonPrimitive("secret"))),
        )
        var connectionAttempts = 0
        val missingRepository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempts++
                FakePluginTransport(MANIFEST)
            },
            services = emptyList(),
            extensionSettingsRepository = StoreBackedSettingsRepository(settingStore),
        )

        val missing = missingRepository.installedPlugins().single()
        assertFalse(missing.installed)
        assertTrue(missing.trusted)
        assertFalse(missing.canClearData)
        assertEquals(SERVICE.packageName, missing.packageName)
        assertEquals(SERVICE.serviceName, missing.serviceName)
        assertEquals(0, connectionAttempts)

        missingRepository.revoke(SERVICE.packageName, SERVICE.serviceName)

        assertTrue(trustStore.trustedServices().isEmpty())
        assertTrue(settingStore.snapshot(EXTENSION_ID.value).values.isEmpty())
        val replacementRepository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            services = listOf(OTHER_SERVICE),
            extensionSettingsRepository = StoreBackedSettingsRepository(settingStore),
        )
        assertTrue(
            replacementRepository.enable(
                OTHER_SERVICE.packageName,
                OTHER_SERVICE.serviceName,
            ) is PluginEnableResult.Enabled
        )
        assertTrue(settingStore.snapshot(EXTENSION_ID.value).values.isEmpty())
    }

    @Test
    fun forgettingOneDuplicateOwnerKeepsDataUntilTheLastOwnerIsForgotten() = runBlocking {
        trust(MANIFEST)
        trust(MANIFEST, service = OTHER_SERVICE)
        settingStore.save(
            EXTENSION_ID.value,
            ExtensionSettingsSnapshot(values = mapOf("section/value" to JsonPrimitive("kept"))),
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            services = emptyList(),
            extensionSettingsRepository = StoreBackedSettingsRepository(settingStore),
        )

        repository.revoke(SERVICE.packageName, SERVICE.serviceName)

        assertTrue(settingStore.snapshot(EXTENSION_ID.value).values.isNotEmpty())
        assertTrue(
            trustStore.isSoleStoredOwner(
                OTHER_SERVICE.packageName,
                OTHER_SERVICE.serviceName,
                EXTENSION_ID.value,
            )
        )

        repository.revoke(OTHER_SERVICE.packageName, OTHER_SERVICE.serviceName)

        assertTrue(settingStore.snapshot(EXTENSION_ID.value).values.isEmpty())
        assertTrue(trustStore.trustedServices().isEmpty())
    }

    private fun repository(
        connector: ExtensionPluginTransportConnector,
        services: List<InstalledExtensionService> = listOf(SERVICE),
        extensionSettingsRepository: ExtensionSettingsRepository = NoOpSettingsRepository,
        activePrincipalRegistry: ActiveExtensionPrincipalRegistry =
            ActiveExtensionPrincipalRegistry(),
    ): TestRepository {
        val runtime = ExtensionRuntime(
            hostApiVersion = ExtensionApiVersions.Current,
            capabilityPolicy = CapabilityPolicy { manifest, _ ->
                val granted = trustStore.grantedCapabilities(manifest.id.value)
                manifest.capabilities.mapNotNullTo(mutableSetOf()) { request ->
                    request.capability.takeIf { capability -> capability.id in granted }
                }
            },
            settingsProvider = settingStore,
        )
        val repository = ExtensionPluginRepositoryImpl.createForTest(
            discovery = ExtensionPluginDiscovery { services },
            trustStore = trustStore,
            transportConnector = connector,
            runtime = runtime,
            extensionSettingsRepository = extensionSettingsRepository,
            extensionSettingStore = settingStore,
            subscriptionProviderImporter = SubscriptionProviderImporter(
                database = database,
                playlistDao = database.playlistDao(),
                channelDao = database.channelDao(),
                providerDao = database.providerDao(),
                programmeDao = database.programmeDao(),
                credentialVault = NoOpCredentialVault,
            ),
            activePrincipalRegistry = activePrincipalRegistry,
            providerAccountOwnerStore = ProviderAccountOwnerStore(
                database = database,
                providerDao = database.providerDao(),
            ),
            settings = context.settings,
        )
        return TestRepository(repository, runtime)
    }

    private fun trust(
        manifest: ExtensionManifest,
        grants: Set<String> = manifest.capabilities.mapTo(mutableSetOf()) { it.capability.id },
        service: InstalledExtensionService = SERVICE,
    ) {
        trustStore.trust(
            service = service,
            extensionId = manifest.id.value,
            capabilities = grants,
            displayName = manifest.displayName,
            version = manifest.extensionVersion.toString(),
            developer = null,
        )
    }

    private fun runtimeExtensions(repository: TestRepository): List<ExtensionId> =
        repository.runtime.registeredExtensions().map { extension -> extension.manifest.id }

    private data class TestRepository(
        val delegate: ExtensionPluginRepositoryImpl,
        val runtime: ExtensionRuntime,
    ) : ExtensionPluginRepository by delegate

    private class FakePluginTransport(
        override val manifest: ExtensionManifest,
    ) : ExtensionPluginTransport {
        var closed = false

        override val isConnectionAvailable: Boolean
            get() = !closed

        override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult =
            error("No hook invocation is expected")

        override suspend fun cancel(invocationId: InvocationId) = Unit

        override suspend fun health(): ExtensionTransportHealth = ExtensionTransportHealth.HEALTHY

        override fun close() {
            closed = true
        }
    }

    private object NoOpSettingsRepository : ExtensionSettingsRepository {
        override suspend fun configuration(
            extensionId: ExtensionId,
            localeTag: String?,
            surface: String,
        ): ExtensionSettingsConfiguration? = null

        override suspend fun update(
            extensionId: ExtensionId,
            sectionId: String,
            fieldKey: String,
            rawValue: String?,
            localeTag: String?,
            surface: String,
        ): ExtensionSettingUpdateResult = error("Settings update is not expected")

        override fun clear(extensionId: ExtensionId) = Unit
    }

    private class StoreBackedSettingsRepository(
        private val store: ExtensionSettingStore,
    ) : ExtensionSettingsRepository {
        override suspend fun configuration(
            extensionId: ExtensionId,
            localeTag: String?,
            surface: String,
        ): ExtensionSettingsConfiguration? = null

        override suspend fun update(
            extensionId: ExtensionId,
            sectionId: String,
            fieldKey: String,
            rawValue: String?,
            localeTag: String?,
            surface: String,
        ): ExtensionSettingUpdateResult = error("Settings update is not expected")

        override fun clear(extensionId: ExtensionId) {
            store.clear(extensionId.value)
        }
    }

    private object NoOpSecretStore : ExtensionSecretStore {
        override fun store(
            extensionId: String,
            settingKey: String,
            secret: String,
            existingHandle: CredentialHandle?,
        ): CredentialHandle = error("Secret storage is not expected")

        override fun resolve(extensionId: String, handle: CredentialHandle): String? = null
        override fun delete(extensionId: String, handle: CredentialHandle) = Unit
        override fun clear(extensionId: String) = Unit
    }

    private object NoOpCredentialVault : CredentialVault {
        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ): ProviderCredentialEntity = error("Credential encryption is not expected")

        override fun decrypt(credential: ProviderCredentialEntity): String? = null
        override fun stage(secret: String): CredentialHandle = error("Credential staging is not expected")
        override fun consume(handle: CredentialHandle): String? = null
    }

    private companion object {
        val EXTENSION_ID = ExtensionId("com.example.lifecycle")
        val SERVICE = InstalledExtensionService(
            packageName = "com.example.lifecycle",
            serviceName = "com.example.lifecycle.ExtensionService",
            certificateSha256 = "certificate",
            uid = 10_001,
        )
        val OTHER_SERVICE = InstalledExtensionService(
            packageName = "com.example.other",
            serviceName = "com.example.other.ExtensionService",
            certificateSha256 = "other-certificate",
            uid = 10_002,
        )
        val MANIFEST = manifest()
        val MANIFEST_WITH_REMOVED_CAPABILITY = manifest(
            ExtensionCapabilityRequest(ExtensionCapabilityIds.SearchRead, "Search channels")
        )

        fun manifest(vararg capabilities: ExtensionCapabilityRequest): ExtensionManifest =
            ExtensionManifest(
                id = EXTENSION_ID,
                displayName = "Lifecycle extension",
                extensionVersion = ExtensionSemanticVersion(1, 1, 0),
                apiRange = ExtensionApiRange(
                    ExtensionApiVersions.Current,
                    ExtensionApiVersions.Current,
                ),
                hooks = emptySet(),
                capabilities = capabilities.toSet(),
            )
    }
}
