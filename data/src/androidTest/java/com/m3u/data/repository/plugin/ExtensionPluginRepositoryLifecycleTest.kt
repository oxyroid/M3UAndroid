package com.m3u.data.repository.plugin

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.settings
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.CredentialVault
import com.m3u.data.extension.security.ExtensionSecretStore
import com.m3u.data.extension.security.InactiveExtensionPrincipalLeaseException
import com.m3u.data.extension.security.ProviderAccountOwnerStore
import com.m3u.data.extension.security.toPrincipal
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.extension.ExtensionEpgRefreshContribution
import com.m3u.data.repository.extension.ExtensionMetadataRefreshContribution
import com.m3u.data.repository.extension.ExtensionSettingEditToken
import com.m3u.data.repository.extension.ExtensionSettingStore
import com.m3u.data.repository.extension.ExtensionSettingUpdateResult
import com.m3u.data.repository.extension.ExtensionSettingsConfiguration
import com.m3u.data.repository.extension.ExtensionSettingsRepository
import com.m3u.data.worker.ExtensionBackgroundTaskScheduler
import com.m3u.data.worker.ExtensionBackgroundWorkOperations
import com.m3u.data.worker.extensionBackgroundWorkName
import com.m3u.data.worker.extensionBackgroundWorkTag
import com.m3u.extension.api.ChannelMetadataPatch
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionBackgroundTaskDeclaration
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionNetworkOrigin
import com.m3u.extension.api.ExtensionProgramme
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.runtime.CapabilityPolicy
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.transport.android.ExtensionTransportIncompatibleException
import com.m3u.extension.transport.android.ExtensionTrustStore
import com.m3u.extension.transport.android.InstalledExtensionService
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    private val repositories = mutableListOf<ExtensionPluginRepositoryImpl>()

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
    fun tearDown() = runBlocking {
        repositories.forEach { repository -> repository.shutdownForTest() }
        repositories.clear()
        database.close()
    }

    @Test
    fun invocationGateObservesKillSwitchBeforeBinderInvocation() = runBlocking {
        trust(MANIFEST)
        val principalRegistry = ActiveExtensionPrincipalRegistry()
        principalRegistry.activate(SERVICE.toPrincipal(EXTENSION_ID))
        val gate = ExtensionInvocationGate(
            settings = context.settings,
            trustStore = trustStore,
            principalRegistry = principalRegistry,
        )

        gate.requireAuthorized(SERVICE, EXTENSION_ID)
        context.settings.edit { preferences ->
            preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = false
        }

        assertTrue(
            runCatching {
                gate.requireAuthorized(SERVICE, EXTENSION_ID)
            }.exceptionOrNull() is SecurityException
        )
    }

    @Test
    fun disableWinsWhileReconnectIsInFlight() = runBlocking {
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
        val disabled = withTimeout(1_000L) {
            repository.disable(EXTENSION_ID.value)
        }
        assertTrue(disabled)
        assertFalse(trustStore.isEnabled(SERVICE))
        releaseConnection.complete(Unit)

        assertEquals(0, reconnect.await())
        assertTrue(runtimeExtensions(repository).isEmpty())
        assertTrue(transport.closed)
    }

    @Test
    fun cancellingReconnectBeforeCommitClosesThePreconnectedTransport() = runBlocking {
        trust(MANIFEST)
        val controlledSettings = ControllableSettingsDataStore()
        lateinit var transport: FakePluginTransport
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(MANIFEST).also { connected ->
                    transport = connected
                    controlledSettings.blockAfterReads(0)
                }
            },
            settings = controlledSettings,
        )

        val listing = async { repository.installedPlugins() }
        controlledSettings.blockedRead.await()
        listing.cancelAndJoin()

        assertTrue(transport.closed)
        assertTrue(runtimeExtensions(repository).isEmpty())
    }

    @Test
    fun cancellingReconnectAfterCommitDoesNotCloseTheRegisteredTransport() = runBlocking {
        trust(MANIFEST)
        val controlledSettings = ControllableSettingsDataStore()
        lateinit var transport: FakePluginTransport
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(MANIFEST).also { connected ->
                    transport = connected
                    controlledSettings.blockAfterReads(2)
                }
            },
            settings = controlledSettings,
        )

        val listing = async { repository.installedPlugins() }
        controlledSettings.blockedRead.await()
        listing.cancelAndJoin()

        assertFalse(transport.closed)
        assertEquals(listOf(EXTENSION_ID), runtimeExtensions(repository))

        assertTrue(repository.disable(EXTENSION_ID.value))
        assertTrue(transport.closed)
        assertTrue(runtimeExtensions(repository).isEmpty())
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
        var connectionAttempt = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempt++
                if (connectionAttempt > 1) {
                    connectionStarted.complete(Unit)
                    releaseConnection.await()
                }
                FakePluginTransport(MANIFEST).also { transport = it }
            }
        )
        val reviewed = repository.installedPlugins().single()
        val authorizationToken = checkNotNull(reviewed.authorizationToken)

        val enable = async {
            repository.enable(
                SERVICE.packageName,
                SERVICE.serviceName,
                authorizationToken,
            )
        }
        connectionStarted.await()
        val revoke = async { repository.revoke(SERVICE.packageName, SERVICE.serviceName) }
        yield()

        assertNull(withTimeoutOrNull(100) { revoke.await() })
        releaseConnection.complete(Unit)

        assertTrue(enable.await() is PluginEnableResult.Rejected)
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
    fun transportHealthQuarantinesAndRecoversAnEnabledRegistration() = runBlocking {
        trust(MANIFEST)
        var health = ExtensionTransportHealth.DEGRADED
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(MANIFEST) { health }
            }
        )

        assertEquals(0, repository.restoreEnabled())
        assertEquals(
            ExtensionState.UNHEALTHY,
            repository.runtime.registeredExtensions().single().state,
        )
        val unhealthy = repository.installedPlugins().single()
        assertTrue(unhealthy.enabled)
        assertEquals(ExtensionState.UNHEALTHY, unhealthy.state)

        health = ExtensionTransportHealth.HEALTHY

        assertEquals(1, repository.restoreEnabled())
        assertEquals(
            ExtensionState.ENABLED,
            repository.runtime.registeredExtensions().single().state,
        )
        assertEquals(ExtensionState.ENABLED, repository.installedPlugins().single().state)
    }

    @Test
    fun healthProbeUsesABoundedDeadlineAndMapsTimeoutToUnhealthy() = runBlocking {
        trust(MANIFEST)
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(MANIFEST) { awaitCancellation() }
            },
            healthCheckTimeoutMillis = 25L,
        )

        val restored = withTimeout(1_000L) { repository.restoreEnabled() }

        assertEquals(0, restored)
        assertEquals(
            ExtensionState.UNHEALTHY,
            repository.runtime.registeredExtensions().single().state,
        )
    }

    @Test
    fun multipleHangingHealthProbesShareOneBoundedDeadlineWindow() = runBlocking {
        val services = numberedServices(3)
        val manifests = services.mapIndexed { index, _ ->
            MANIFEST.copy(id = ExtensionId("com.example.lifecycle.$index"))
        }
        services.zip(manifests).forEach { (service, manifest) ->
            trust(manifest, service = service)
        }
        var hangHealth = false
        val repository = repository(
            services = services,
            connector = ExtensionPluginTransportConnector { service ->
                val manifest = manifests[services.indexOf(service)]
                FakePluginTransport(manifest) {
                    if (hangHealth) awaitCancellation()
                    ExtensionTransportHealth.HEALTHY
                }
            },
            healthCheckTimeoutMillis = 100L,
            maxConcurrentPluginInspections = services.size,
        )
        assertEquals(services.size, repository.restoreEnabled())
        hangHealth = true

        val startedAt = SystemClock.elapsedRealtime()
        val plugins = repository.installedPlugins()
        val elapsedMillis = SystemClock.elapsedRealtime() - startedAt

        assertTrue("health probes took ${elapsedMillis}ms", elapsedMillis < 250L)
        assertEquals(
            List(services.size) { ExtensionState.UNHEALTHY },
            plugins.map(InstalledPlugin::state),
        )
    }

    @Test
    fun pluginInspectionBoundsConcurrencyAndPreservesOutputOrder() = runBlocking {
        val services = numberedServices(5).reversed()
        val activeConnections = AtomicInteger()
        val maximumConnections = AtomicInteger()
        val startedConnections = AtomicInteger()
        val firstWaveStarted = CompletableDeferred<Unit>()
        val releaseConnections = CompletableDeferred<Unit>()
        val repository = repository(
            services = services,
            connector = ExtensionPluginTransportConnector {
                val active = activeConnections.incrementAndGet()
                maximumConnections.updateAndGet { current -> maxOf(current, active) }
                if (startedConnections.incrementAndGet() == 2) {
                    firstWaveStarted.complete(Unit)
                }
                try {
                    releaseConnections.await()
                    FakePluginTransport(MANIFEST)
                } finally {
                    activeConnections.decrementAndGet()
                }
            },
            maxConcurrentPluginInspections = 2,
        )

        val listing = async { repository.installedPlugins() }
        firstWaveStarted.await()
        yield()

        assertEquals(2, startedConnections.get())
        assertEquals(2, maximumConnections.get())
        releaseConnections.complete(Unit)
        val plugins = listing.await()

        assertEquals(2, maximumConnections.get())
        assertEquals(
            services.map(InstalledExtensionService::packageName).sorted(),
            plugins.map(InstalledPlugin::packageName),
        )
    }

    @Test
    fun healthProbeDoesNotHoldTheLifecycleMutex() = runBlocking {
        trust(MANIFEST)
        val healthStarted = CompletableDeferred<Unit>()
        val releaseHealth = CompletableDeferred<Unit>()
        var blockHealth = false
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(MANIFEST) {
                    if (blockHealth) {
                        healthStarted.complete(Unit)
                        releaseHealth.await()
                    }
                    ExtensionTransportHealth.HEALTHY
                }
            },
            healthCheckTimeoutMillis = 5_000L,
        )
        assertEquals(1, repository.restoreEnabled())
        blockHealth = true

        val listing = async { repository.installedPlugins() }
        healthStarted.await()
        val disabled = withTimeout(1_000L) {
            repository.disable(EXTENSION_ID.value)
        }
        releaseHealth.complete(Unit)
        listing.await()

        assertTrue(disabled)
        assertFalse(trustStore.isEnabled(SERVICE))
    }

    @Test
    fun staleHealthProbeDoesNotQuarantineAReplacementRegistration() = runBlocking {
        trust(MANIFEST)
        val staleHealthStarted = CompletableDeferred<Unit>()
        val releaseStaleHealth = CompletableDeferred<Unit>()
        lateinit var staleTransport: FakePluginTransport
        var connectionAttempt = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempt++
                if (connectionAttempt == 1) {
                    FakePluginTransport(MANIFEST) {
                        staleHealthStarted.complete(Unit)
                        releaseStaleHealth.await()
                        ExtensionTransportHealth.UNAVAILABLE
                    }.also { staleTransport = it }
                } else {
                    FakePluginTransport(MANIFEST)
                }
            },
            healthCheckTimeoutMillis = 5_000L,
        )

        val staleRestore = async { repository.restoreEnabled() }
        staleHealthStarted.await()
        staleTransport.connectionAvailable = false

        assertEquals(1, repository.restoreEnabled())
        releaseStaleHealth.complete(Unit)
        assertEquals(0, staleRestore.await())
        assertEquals(
            ExtensionState.ENABLED,
            repository.runtime.registeredExtensions().single().state,
        )
    }

    @Test
    fun explicitEnableSchedulesWorkOnlyAfterAHealthyProbe() = runBlocking {
        val manifest = backgroundManifest(requiresNetwork = false)
        val workOperations = RecordingBackgroundWorkOperations()
        var sessionCleanupRequests = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(manifest) {
                    ExtensionTransportHealth.DEGRADED
                }
            },
            backgroundTaskScheduler = ExtensionBackgroundTaskScheduler(workOperations),
            scheduleSessionCleanup = { sessionCleanupRequests++ },
        )

        val result = repository.enable(SERVICE.packageName, SERVICE.serviceName)

        assertTrue(result is PluginEnableResult.Rejected)
        assertTrue(trustStore.isEnabled(SERVICE))
        assertEquals(
            ExtensionState.UNHEALTHY,
            repository.runtime.registeredExtensions().single().state,
        )
        assertTrue(workOperations.enqueued.isEmpty())
        assertEquals(0, sessionCleanupRequests)
    }

    @Test
    fun transientConnectionFailureIsUnhealthyButProtocolFailureIsIncompatible() = runBlocking {
        trust(MANIFEST)
        val unavailableRepository = repository(
            connector = ExtensionPluginTransportConnector {
                error("transient connection failure")
            }
        )

        assertEquals(0, unavailableRepository.restoreEnabled())
        val unavailable = unavailableRepository.installedPlugins().single()
        assertTrue(unavailable.enabled)
        assertEquals(ExtensionState.UNHEALTHY, unavailable.state)
        assertTrue(trustStore.isEnabled(SERVICE))

        val incompatibleRepository = repository(
            connector = ExtensionPluginTransportConnector {
                throw ExtensionTransportIncompatibleException(
                    "Unsupported transport protocol"
                )
            }
        )

        assertEquals(0, incompatibleRepository.restoreEnabled())
        val incompatible = incompatibleRepository.installedPlugins().single()
        assertFalse(incompatible.enabled)
        assertEquals(ExtensionState.INCOMPATIBLE, incompatible.state)
        assertFalse(trustStore.isEnabled(SERVICE))
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
    fun automaticReconnectDoesNotGrantNewOptionalCapability() = runBlocking {
        val updatedManifest = manifest(
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.SearchRead,
                reason = "Search channels",
                required = false,
            )
        )
        trust(MANIFEST)
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(updatedManifest)
            }
        )

        assertEquals(1, repository.restoreEnabled())

        assertTrue(trustStore.grantedCapabilities(SERVICE).isEmpty())
        assertTrue(trustStore.isEnabled(SERVICE))
    }

    @Test
    fun automaticReconnectCapabilityShrinkCancelsRemovedBackgroundWork() = runBlocking {
        val oldManifest = backgroundManifest(requiresNetwork = true)
        trust(oldManifest)
        val staleWorkName = extensionBackgroundWorkName(
            EXTENSION_ID,
            oldManifest.backgroundTasks.single().taskId,
        )
        val workOperations = RecordingBackgroundWorkOperations(
            existingTags = listOf(
                setOf(extensionBackgroundWorkTag(EXTENSION_ID), staleWorkName)
            )
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            backgroundTaskScheduler = ExtensionBackgroundTaskScheduler(workOperations),
        )

        assertEquals(1, repository.restoreEnabled())

        assertTrue(staleWorkName in workOperations.cancelledUniqueWork)
        assertTrue(workOperations.enqueued.isEmpty())
        assertTrue(trustStore.grantedCapabilities(EXTENSION_ID.value).isEmpty())
        assertTrue(trustStore.isEnabled(SERVICE))
    }

    @Test
    fun automaticReconnectWithNewRequiredCapabilityCancelsWorkAndRequiresReauthorization() =
        runBlocking {
            trust(backgroundManifest(requiresNetwork = false))
            val workOperations = RecordingBackgroundWorkOperations()
            val repository = repository(
                connector = ExtensionPluginTransportConnector {
                    FakePluginTransport(backgroundManifest(requiresNetwork = true))
                },
                backgroundTaskScheduler = ExtensionBackgroundTaskScheduler(workOperations),
            )

            assertEquals(0, repository.restoreEnabled())

            assertEquals(
                listOf(extensionBackgroundWorkTag(EXTENSION_ID)),
                workOperations.cancelledTags,
            )
            assertFalse(trustStore.isEnabled(SERVICE))
            assertTrue(runtimeExtensions(repository).isEmpty())
        }

    @Test
    fun backgroundWorkOperationFailureIsRedactedInDiagnostics() = runBlocking {
        val manifest = backgroundManifest(requiresNetwork = false)
        val workOperations = RecordingBackgroundWorkOperations(
            enqueueFailure = IllegalStateException("secret scheduler detail"),
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(manifest) },
            backgroundTaskScheduler = ExtensionBackgroundTaskScheduler(workOperations),
        )

        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Enabled
        )
        val diagnostics = checkNotNull(repository.diagnostics(EXTENSION_ID.value))

        assertTrue(
            diagnostics.contains(
                "\"backgroundSchedulingError\": \"IllegalStateException\""
            )
        )
        assertFalse(diagnostics.contains("secret scheduler detail"))
    }

    @Test
    fun sessionCleanupFailureDoesNotRollBackCommittedEnable() = runBlocking {
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(MANIFEST)
            },
            scheduleSessionCleanup = {
                throw IllegalStateException("private cleanup detail")
            },
        )

        val result = repository.enable(SERVICE.packageName, SERVICE.serviceName)
        val diagnostics = checkNotNull(repository.diagnostics(EXTENSION_ID.value))

        assertTrue(result is PluginEnableResult.Enabled)
        assertTrue(
            diagnostics.contains(
                "\"backgroundSchedulingError\": \"IllegalStateException\""
            )
        )
        assertFalse(diagnostics.contains("private cleanup detail"))
    }

    @Test
    fun explicitEnableSchedulesExistingNonEpgPlaylistsForContributionHooks() = runBlocking {
        database.playlistDao().insertOrReplaceAll(
            Playlist("M3U", "https://media.example/list.m3u", source = DataSource.M3U),
            Playlist("Provider", "m3u-provider://account/one", source = DataSource.Provider),
            Playlist("EPG", "https://media.example/guide.xml", source = DataSource.EPG),
        )
        val scheduler = RecordingContributionScheduler()
        val manifest = contributionManifest()
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(manifest)
            },
            extensionContributionScheduler = scheduler,
        )

        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Enabled
        )

        assertEquals(
            setOf(
                "https://media.example/list.m3u",
                "m3u-provider://account/one",
            ),
            scheduler.enqueued.toSet(),
        )
    }

    @Test
    fun automaticRestoreDoesNotForceImmediateContributionRefresh() = runBlocking {
        val manifest = contributionManifest()
        trust(manifest)
        database.playlistDao().insertOrReplace(
            Playlist("M3U", "https://media.example/list.m3u", source = DataSource.M3U)
        )
        val scheduler = RecordingContributionScheduler()
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(manifest)
            },
            extensionContributionScheduler = scheduler,
        )

        assertEquals(1, repository.restoreEnabled())

        assertTrue(scheduler.enqueued.isEmpty())
    }

    @Test
    fun globalReenableSchedulesContributionsAfterRestoringPlugins() = runBlocking {
        val manifest = contributionManifest()
        trust(manifest)
        database.playlistDao().insertOrReplace(
            Playlist("M3U", "https://media.example/list.m3u", source = DataSource.M3U)
        )
        val scheduler = RecordingContributionScheduler()
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(manifest)
            },
            extensionContributionScheduler = scheduler,
            observeSettingsChanges = true,
        )
        withTimeout(5_000) {
            while (runtimeExtensions(repository).isEmpty()) yield()
        }
        assertTrue(scheduler.enqueued.isEmpty())

        context.settings.edit { preferences ->
            preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = false
        }
        withTimeout(5_000) {
            while (runtimeExtensions(repository).isNotEmpty()) yield()
        }
        context.settings.edit { preferences ->
            preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] = true
        }
        withTimeout(5_000) {
            while ("https://media.example/list.m3u" !in scheduler.enqueued) yield()
        }

        assertEquals(listOf("https://media.example/list.m3u"), scheduler.enqueued)
    }

    @Test
    fun disablingTheLastContributionExtensionCancelsAllPlaylistWork() = runBlocking {
        val secondExtensionId = ExtensionId("com.example.lifecycle.second")
        val secondService = OTHER_SERVICE.copy(
            packageName = "com.example.lifecycle.second",
            serviceName = "com.example.lifecycle.second.ExtensionService",
        )
        val firstManifest = contributionManifest()
        val secondManifest = contributionManifest(secondExtensionId)
        database.playlistDao().insertOrReplaceAll(
            Playlist("M3U", "https://media.example/list.m3u", source = DataSource.M3U),
            Playlist("Provider", "m3u-provider://account/one", source = DataSource.Provider),
            Playlist("EPG", "https://media.example/guide.xml", source = DataSource.EPG),
        )
        val scheduler = RecordingContributionScheduler()
        val repository = repository(
            connector = ExtensionPluginTransportConnector { service ->
                FakePluginTransport(
                    if (service.key == SERVICE.key) firstManifest else secondManifest
                )
            },
            services = listOf(SERVICE, secondService),
            extensionContributionScheduler = scheduler,
        )
        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Enabled
        )
        assertTrue(
            repository.enable(secondService.packageName, secondService.serviceName) is
                PluginEnableResult.Enabled
        )
        scheduler.cancelled.clear()

        repository.disable(EXTENSION_ID.value)

        assertTrue(scheduler.cancelled.isEmpty())

        repository.disable(secondExtensionId.value)

        assertEquals(
            setOf("https://media.example/list.m3u", "m3u-provider://account/one"),
            scheduler.cancelled.toSet(),
        )
    }

    @Test
    fun revokingTheLastContributionExtensionCancelsAllPlaylistWork() = runBlocking {
        database.playlistDao().insertOrReplaceAll(
            Playlist("M3U", "https://media.example/list.m3u", source = DataSource.M3U),
            Playlist("Provider", "m3u-provider://account/one", source = DataSource.Provider),
            Playlist("EPG", "https://media.example/guide.xml", source = DataSource.EPG),
        )
        val scheduler = RecordingContributionScheduler()
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(contributionManifest())
            },
            extensionContributionScheduler = scheduler,
        )
        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Enabled
        )
        scheduler.cancelled.clear()

        repository.revoke(SERVICE.packageName, SERVICE.serviceName)

        assertEquals(
            setOf("https://media.example/list.m3u", "m3u-provider://account/one"),
            scheduler.cancelled.toSet(),
        )
    }

    @Test
    fun pruningTheLastMissingContributionExtensionCancelsAllPlaylistWork() = runBlocking {
        trust(contributionManifest())
        database.playlistDao().insertOrReplaceAll(
            Playlist("M3U", "https://media.example/list.m3u", source = DataSource.M3U),
            Playlist("Provider", "m3u-provider://account/one", source = DataSource.Provider),
            Playlist("EPG", "https://media.example/guide.xml", source = DataSource.EPG),
        )
        val scheduler = RecordingContributionScheduler()
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                error("A missing extension must not be connected")
            },
            services = emptyList(),
            extensionContributionScheduler = scheduler,
        )

        assertEquals(0, repository.restoreEnabled())

        assertEquals(
            setOf("https://media.example/list.m3u", "m3u-provider://account/one"),
            scheduler.cancelled.toSet(),
        )
    }

    @Test
    fun restoreClearsContributionsWhoseHooksWereRemovedAndCanReplayCleanup() = runBlocking {
        trust(allContributionManifest())
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(MANIFEST)
            }
        )
        seedExtensionContributions(repository)

        assertEquals(1, repository.restoreEnabled())
        assertExtensionContributionsCleared()

        assertEquals(1, repository.restoreEnabled())
        assertExtensionContributionsCleared()
    }

    @Test
    fun restoreClearsOnlyTheContributionTypeWhoseAuthorizationWasRemoved() = runBlocking {
        trust(allContributionManifest())
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(epgContributionManifest())
            }
        )
        seedExtensionContributions(repository)

        assertEquals(1, repository.restoreEnabled())

        assertEquals(
            "Original title",
            database.channelDao().getByPlaylistUrlAndRelationId(
                CONTRIBUTION_PLAYLIST_URL,
                CONTRIBUTION_CHANNEL_REFERENCE,
            )?.title,
        )
        assertTrue(
            checkNotNull(database.playlistDao().get(CONTRIBUTION_PLAYLIST_URL))
                .epgUrls
                .any { source ->
                    source.startsWith("m3u-extension-epg://${EXTENSION_ID.value}/")
                }
        )
    }

    @Test
    fun restoreClearsContributionsForAnExtensionPersistedAsDisabled() = runBlocking {
        trust(allContributionManifest())
        trustStore.setEnabled(SERVICE, false)
        var connectionAttempts = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempts++
                FakePluginTransport(allContributionManifest())
            }
        )
        seedExtensionContributions(repository)

        assertEquals(0, repository.restoreEnabled())
        assertExtensionContributionsCleared()
        assertEquals(0, connectionAttempts)
    }

    @Test
    fun restoreClearsContributionsWhenTheExtensionBecomesIncompatible() = runBlocking {
        trust(allContributionManifest())
        val incompatibleService = SERVICE.copy(
            incompatibilityReason = "Extension package is incompatible"
        )
        var connectionAttempts = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempts++
                FakePluginTransport(allContributionManifest())
            },
            services = listOf(incompatibleService),
        )
        seedExtensionContributions(repository)

        assertEquals(0, repository.restoreEnabled())
        assertExtensionContributionsCleared()
        assertFalse(trustStore.isEnabled(SERVICE))
        assertEquals(0, connectionAttempts)
    }

    @Test
    fun disabledReauthorizationDoesNotScheduleContributions() = runBlocking {
        val manifest = contributionManifest()
        trust(manifest)
        trustStore.setEnabled(SERVICE, false)
        database.playlistDao().insertOrReplace(
            Playlist("M3U", "https://media.example/list.m3u", source = DataSource.M3U)
        )
        val scheduler = RecordingContributionScheduler()
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(manifest)
            },
            extensionContributionScheduler = scheduler,
        )

        assertTrue(
            repository.reauthorize(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Enabled
        )

        assertTrue(scheduler.enqueued.isEmpty())
    }

    @Test
    fun contributionSchedulingFailureDoesNotRollBackEnableAndIsRedacted() = runBlocking {
        val manifest = contributionManifest()
        database.playlistDao().insertOrReplace(
            Playlist("M3U", "https://media.example/list.m3u", source = DataSource.M3U)
        )
        val scheduler = RecordingContributionScheduler(
            enqueueFailure = IllegalStateException("private work manager detail")
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(manifest)
            },
            extensionContributionScheduler = scheduler,
        )

        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName) is
                PluginEnableResult.Enabled
        )
        val diagnostics = checkNotNull(repository.diagnostics(EXTENSION_ID.value))

        assertTrue(
            diagnostics.contains(
                "\"contributionSchedulingError\": \"IllegalStateException\""
            )
        )
        assertFalse(diagnostics.contains("private work manager detail"))
    }

    @Test
    fun connectorCancellationIsPropagatedWithoutTrustCommit() = runBlocking {
        var connectionAttempt = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempt++
                if (connectionAttempt > 1) {
                    throw CancellationException("caller cancelled")
                }
                FakePluginTransport(MANIFEST)
            }
        )
        val authorizationToken = checkNotNull(
            repository.installedPlugins().single().authorizationToken
        )

        var propagated = false
        try {
            repository.enable(
                SERVICE.packageName,
                SERVICE.serviceName,
                authorizationToken,
            )
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
        trustStore.setEnabled(SERVICE, false)
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
    fun changedManifestAfterReviewIsRejectedWithoutGrantingTrust() = runBlocking {
        val reviewedOrigin = ExtensionNetworkOrigin("https://reviewed.example")
        val changedOrigin = ExtensionNetworkOrigin("https://changed.example")
        val reviewedManifest = networkManifest(setOf(reviewedOrigin))
        val changedManifest = networkManifest(setOf(changedOrigin))
        var connectionAttempt = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempt++
                FakePluginTransport(
                    if (connectionAttempt == 1) reviewedManifest else changedManifest
                )
            }
        )
        val reviewed = repository.installedPlugins().single()

        val result = repository.enable(
            SERVICE.packageName,
            SERVICE.serviceName,
            checkNotNull(reviewed.authorizationToken),
        )

        assertTrue(result is PluginEnableResult.Rejected)
        assertTrue((result as PluginEnableResult.Rejected).reason.contains("review"))
        assertFalse(trustStore.isTrusted(SERVICE))
        assertTrue(trustStore.grantedCapabilities(SERVICE).isEmpty())
        assertTrue(trustStore.approvedNetworkOrigins(SERVICE).isEmpty())
        assertTrue(runtimeExtensions(repository).isEmpty())
    }

    @Test
    fun authorizationTokenIsSingleUse() = runBlocking {
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) }
        )
        val token = checkNotNull(
            repository.installedPlugins().single().authorizationToken
        )

        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName, token) is
                PluginEnableResult.Enabled
        )
        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName, token) is
                PluginEnableResult.Rejected
        )
    }

    @Test
    fun latestReviewReplacesThePreviousAuthorizationTokenForAService() = runBlocking {
        var tokenNumber = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            authorizationTokenFactory = {
                tokenNumber++
                PluginAuthorizationToken("token-$tokenNumber")
            },
        )
        val firstToken = checkNotNull(
            repository.installedPlugins().single().authorizationToken
        )
        val latestToken = checkNotNull(
            repository.installedPlugins().single().authorizationToken
        )

        assertNotEquals(firstToken, latestToken)
        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName, firstToken) is
                PluginEnableResult.Rejected
        )
        assertTrue(
            repository.enable(SERVICE.packageName, SERVICE.serviceName, latestToken) is
                PluginEnableResult.Enabled
        )
    }

    @Test
    fun expiredAuthorizationTokenRequiresAnotherReview() = runBlocking {
        var now = 1_000L
        var connectionAttempts = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempts++
                FakePluginTransport(MANIFEST)
            },
            nowElapsedRealtimeMillis = { now },
        )
        val token = checkNotNull(
            repository.installedPlugins().single().authorizationToken
        )
        now += 5 * 60 * 1_000L + 1

        val result = repository.enable(
            SERVICE.packageName,
            SERVICE.serviceName,
            token,
        )

        assertTrue(result is PluginEnableResult.Rejected)
        assertFalse(trustStore.isTrusted(SERVICE))
        assertEquals(1, connectionAttempts)
    }

    @Test
    fun explicitReauthorizationRepinsChangedCertificateWithoutAutomaticallyEnabling() = runBlocking {
        trust(MANIFEST)
        val replacement = SERVICE.copy(certificateSha256 = "replacement-certificate")
        var cleanupSchedules = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            services = listOf(replacement),
            scheduleSessionCleanup = { cleanupSchedules++ },
        )

        val result = repository.reauthorize(
            replacement.packageName,
            replacement.serviceName,
        )

        assertTrue(result is PluginEnableResult.Enabled)
        assertTrue(trustStore.isTrusted(replacement))
        assertFalse(trustStore.isTrusted(SERVICE))
        assertFalse(trustStore.isEnabled(replacement))
        assertEquals(0, cleanupSchedules)
    }

    @Test
    fun explicitReauthorizationPreservesDisabledState() = runBlocking {
        trust(MANIFEST)
        trustStore.setEnabled(SERVICE, false)
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
        )

        val result = repository.reauthorize(SERVICE.packageName, SERVICE.serviceName)

        assertTrue(result is PluginEnableResult.Enabled)
        assertFalse(trustStore.isEnabled(SERVICE))
        assertTrue(runtimeExtensions(repository).isEmpty())
    }

    @Test
    fun certificateRepinPreservesDisabledStateWithoutReconnect() = runBlocking {
        trust(MANIFEST)
        trustStore.setEnabled(SERVICE, false)
        val replacement = SERVICE.copy(certificateSha256 = "replacement-certificate")
        var connectionAttempts = 0
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                connectionAttempts++
                FakePluginTransport(MANIFEST)
            },
            services = listOf(replacement),
        )

        val result = repository.reauthorize(
            replacement.packageName,
            replacement.serviceName,
        )

        assertTrue(result is PluginEnableResult.Enabled)
        assertTrue(trustStore.isTrusted(replacement))
        assertFalse(trustStore.isEnabled(replacement))
        assertEquals(2, connectionAttempts)
        assertTrue(runtimeExtensions(repository).isEmpty())
    }

    @Test
    fun certificateRepinDoesNotApproveNewManifestNetworkOrigins() = runBlocking {
        val retainedOrigin = ExtensionNetworkOrigin("https://retained.example")
        val removedOrigin = ExtensionNetworkOrigin("https://removed.example")
        val newOrigin = ExtensionNetworkOrigin("https://new.example")
        val originalManifest = networkManifest(setOf(retainedOrigin, removedOrigin))
        val replacementManifest = networkManifest(setOf(retainedOrigin, newOrigin))
        trust(originalManifest)
        val replacement = SERVICE.copy(certificateSha256 = "replacement-certificate")
        val repository = repository(
            connector = ExtensionPluginTransportConnector {
                FakePluginTransport(replacementManifest)
            },
            services = listOf(replacement),
        )

        val result = repository.reauthorize(
            replacement.packageName,
            replacement.serviceName,
        )

        assertTrue(result is PluginEnableResult.Enabled)
        assertEquals(
            setOf(retainedOrigin.canonicalValue),
            trustStore.approvedNetworkOrigins(replacement),
        )
        val installed = repository.installedPlugins().single()
        assertEquals(
            setOf(retainedOrigin.canonicalValue, newOrigin.canonicalValue),
            installed.networkOrigins,
        )
        assertEquals(setOf(retainedOrigin.canonicalValue), installed.approvedNetworkOrigins)
    }

    @Test
    fun explicitEnableTransfersSameSignerServiceRename() = runBlocking {
        trust(MANIFEST)
        val replacement = SERVICE.copy(
            serviceName = "com.example.lifecycle.RenamedExtensionService"
        )
        val repository = repository(
            connector = ExtensionPluginTransportConnector { FakePluginTransport(MANIFEST) },
            services = listOf(replacement),
        )

        val result = repository.enable(
            replacement.packageName,
            replacement.serviceName,
        )

        assertTrue(result is PluginEnableResult.Enabled)
        assertTrue(trustStore.isTrusted(replacement))
        assertFalse(
            trustStore.trustedServices().any { trusted ->
                trusted.serviceName == SERVICE.serviceName
            }
        )
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
            repository.enable(
                SERVICE.packageName,
                SERVICE.serviceName,
                INVALID_AUTHORIZATION_TOKEN,
            ) is
                PluginEnableResult.Rejected
        )
        assertFalse(trustStore.isEnabled(SERVICE))
        trust(MANIFEST)
        assertTrue(
            repository.reauthorize(
                SERVICE.packageName,
                SERVICE.serviceName,
                INVALID_AUTHORIZATION_TOKEN,
            ) is
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

    private suspend fun seedExtensionContributions(repository: TestRepository) {
        database.playlistDao().insertOrReplace(
            Playlist(
                title = "Contribution lifecycle",
                url = CONTRIBUTION_PLAYLIST_URL,
                source = DataSource.M3U,
            )
        )
        database.channelDao().insertOrReplace(
            Channel(
                url = "https://media.example/live.m3u8",
                category = "Original category",
                title = "Original title",
                playlistUrl = CONTRIBUTION_PLAYLIST_URL,
                relationId = CONTRIBUTION_CHANNEL_REFERENCE,
            )
        )
        repository.importer.applyMetadataEnrichment(
            CONTRIBUTION_PLAYLIST_URL,
            listOf(
                ExtensionMetadataRefreshContribution(
                    extensionId = EXTENSION_ID,
                    patches = listOf(
                        ChannelMetadataPatch(
                            stableReference = CONTRIBUTION_CHANNEL_REFERENCE,
                            title = "Extension title",
                        )
                    ),
                )
            ),
        )
        repository.importer.replaceExtensionEpg(
            CONTRIBUTION_PLAYLIST_URL,
            listOf(
                ExtensionEpgRefreshContribution(
                    extensionId = EXTENSION_ID,
                    programmes = listOf(
                        ExtensionProgramme(
                            channelReference = CONTRIBUTION_CHANNEL_REFERENCE,
                            title = "Extension programme",
                            startEpochMillis = 1_000,
                            endEpochMillis = 2_000,
                        )
                    ),
                )
            ),
        )
        assertEquals(
            "Extension title",
            database.channelDao().getByPlaylistUrlAndRelationId(
                CONTRIBUTION_PLAYLIST_URL,
                CONTRIBUTION_CHANNEL_REFERENCE,
            )?.title,
        )
        assertTrue(
            checkNotNull(database.playlistDao().get(CONTRIBUTION_PLAYLIST_URL))
                .epgUrls
                .any { source ->
                    source.startsWith("m3u-extension-epg://${EXTENSION_ID.value}/")
                }
        )
    }

    private suspend fun assertExtensionContributionsCleared() {
        assertEquals(
            "Original title",
            database.channelDao().getByPlaylistUrlAndRelationId(
                CONTRIBUTION_PLAYLIST_URL,
                CONTRIBUTION_CHANNEL_REFERENCE,
            )?.title,
        )
        assertTrue(
            checkNotNull(database.playlistDao().get(CONTRIBUTION_PLAYLIST_URL))
                .epgUrls
                .none { source ->
                    source.startsWith("m3u-extension-epg://${EXTENSION_ID.value}/")
                }
        )
    }

    private fun repository(
        connector: ExtensionPluginTransportConnector,
        services: List<InstalledExtensionService> = listOf(SERVICE),
        extensionSettingsRepository: ExtensionSettingsRepository = NoOpSettingsRepository,
        activePrincipalRegistry: ActiveExtensionPrincipalRegistry =
            ActiveExtensionPrincipalRegistry(),
        backgroundTaskScheduler: ExtensionBackgroundTaskScheduler? = null,
        extensionContributionScheduler: ExtensionContributionScheduler? = null,
        scheduleSessionCleanup: () -> Unit = {},
        observeSettingsChanges: Boolean = false,
        settings: Settings = context.settings,
        nowElapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
        connectTimeoutMillis: Long = 5_000L,
        healthCheckTimeoutMillis: Long = 2_000L,
        maxConcurrentPluginInspections: Int = 4,
        authorizationTokenFactory: () -> PluginAuthorizationToken = {
            PluginAuthorizationToken("test-token-${System.nanoTime()}")
        },
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
        val importer = SubscriptionProviderImporter(
            database = database,
            playlistDao = database.playlistDao(),
            channelDao = database.channelDao(),
            providerDao = database.providerDao(),
            programmeDao = database.programmeDao(),
            credentialVault = NoOpCredentialVault,
        )
        val repository = ExtensionPluginRepositoryImpl.createForTest(
            discovery = ExtensionPluginDiscovery { services },
            trustStore = trustStore,
            transportConnector = connector,
            runtime = runtime,
            extensionSettingsRepository = extensionSettingsRepository,
            extensionSettingStore = settingStore,
            subscriptionProviderImporter = importer,
            activePrincipalRegistry = activePrincipalRegistry,
            providerAccountOwnerStore = ProviderAccountOwnerStore(
                database = database,
                providerDao = database.providerDao(),
            ),
            settings = settings,
            backgroundTaskScheduler = backgroundTaskScheduler,
            playlistDao = database.playlistDao(),
            extensionContributionScheduler = extensionContributionScheduler,
            scheduleSessionCleanup = scheduleSessionCleanup,
            observeSettingsChanges = observeSettingsChanges,
            nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
            connectTimeoutMillis = connectTimeoutMillis,
            healthCheckTimeoutMillis = healthCheckTimeoutMillis,
            maxConcurrentPluginInspections = maxConcurrentPluginInspections,
            authorizationTokenFactory = authorizationTokenFactory,
        )
        repositories += repository
        return TestRepository(repository, runtime, importer)
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
            networkOrigins = manifest.networkOrigins
                .mapTo(mutableSetOf()) { origin -> origin.canonicalValue },
        )
    }

    private fun numberedServices(count: Int): List<InstalledExtensionService> =
        List(count) { index ->
            InstalledExtensionService(
                packageName = "com.example.lifecycle.$index",
                serviceName = "com.example.lifecycle.$index.ExtensionService",
                certificateSha256 = "certificate-$index",
                uid = 11_000 + index,
            )
        }

    private fun runtimeExtensions(repository: TestRepository): List<ExtensionId> =
        repository.runtime.registeredExtensions().map { extension -> extension.manifest.id }

    private data class TestRepository(
        val delegate: ExtensionPluginRepositoryImpl,
        val runtime: ExtensionRuntime,
        val importer: SubscriptionProviderImporter,
    ) : ExtensionPluginRepository by delegate {
        suspend fun enable(
            packageName: String,
            serviceName: String,
        ): PluginEnableResult {
            val plugin = delegate.installedPlugins().single { installed ->
                installed.packageName == packageName && installed.serviceName == serviceName
            }
            return delegate.enable(
                packageName,
                serviceName,
                checkNotNull(plugin.authorizationToken),
            )
        }

        suspend fun reauthorize(
            packageName: String,
            serviceName: String,
        ): PluginEnableResult {
            val plugin = delegate.installedPlugins().single { installed ->
                installed.packageName == packageName && installed.serviceName == serviceName
            }
            return delegate.reauthorize(
                packageName,
                serviceName,
                checkNotNull(plugin.authorizationToken),
            )
        }
    }

    private class ControllableSettingsDataStore : DataStore<Preferences> {
        private val stateLock = Any()
        private var preferences: Preferences = preferencesOf(
            PreferencesKeys.EXTERNAL_EXTENSIONS to true
        )
        private var remainingReadsBeforeBlock: Int? = null
        val blockedRead = CompletableDeferred<Unit>()

        override val data: Flow<Preferences>
            get() {
                val (snapshot, shouldBlock) = synchronized(stateLock) {
                    val remaining = remainingReadsBeforeBlock
                    val block = remaining == 0
                    if (block) {
                        remainingReadsBeforeBlock = null
                    } else if (remaining != null) {
                        remainingReadsBeforeBlock = remaining - 1
                    }
                    preferences to block
                }
                return if (shouldBlock) {
                    flow {
                        blockedRead.complete(Unit)
                        awaitCancellation()
                    }
                } else {
                    flowOf(snapshot)
                }
            }

        fun blockAfterReads(readsBeforeBlock: Int) {
            require(readsBeforeBlock >= 0)
            synchronized(stateLock) {
                check(remainingReadsBeforeBlock == null)
                remainingReadsBeforeBlock = readsBeforeBlock
            }
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val current = synchronized(stateLock) { preferences }
            val updated = transform(current)
            synchronized(stateLock) { preferences = updated }
            return updated
        }
    }

    private class FakePluginTransport(
        override val manifest: ExtensionManifest,
        private val healthCheck: suspend () -> ExtensionTransportHealth = {
            ExtensionTransportHealth.HEALTHY
        },
    ) : ExtensionPluginTransport {
        var closed = false
        var connectionAvailable = true

        override val isConnectionAvailable: Boolean
            get() = connectionAvailable && !closed

        override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult =
            error("No hook invocation is expected")

        override suspend fun cancel(invocationId: InvocationId) = Unit

        override suspend fun health(): ExtensionTransportHealth = healthCheck()

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
            editToken: ExtensionSettingEditToken,
            rawValue: String?,
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
            editToken: ExtensionSettingEditToken,
            rawValue: String?,
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
        const val CONTRIBUTION_PLAYLIST_URL = "https://media.example/contribution-list.m3u"
        const val CONTRIBUTION_CHANNEL_REFERENCE = "contribution-channel"
        val INVALID_AUTHORIZATION_TOKEN = PluginAuthorizationToken("invalid")
        val MANIFEST_WITH_REMOVED_CAPABILITY = manifest(
            ExtensionCapabilityRequest(ExtensionCapabilityIds.SearchRead, "Search channels")
        )

        fun backgroundManifest(requiresNetwork: Boolean): ExtensionManifest {
            val requiredCapabilities = buildSet {
                add(ExtensionCapabilityIds.BackgroundTask)
                if (requiresNetwork) add(ExtensionCapabilityIds.Network)
            }
            val capabilities = buildSet {
                add(
                    ExtensionCapabilityRequest(
                        ExtensionCapabilityIds.BackgroundTask,
                        "Run background maintenance",
                    )
                )
                if (requiresNetwork) {
                    add(
                        ExtensionCapabilityRequest(
                            ExtensionCapabilityIds.Network,
                            "Refresh remote data",
                        )
                    )
                }
            }
            return ExtensionManifest(
                id = EXTENSION_ID,
                displayName = "Lifecycle extension",
                extensionVersion = ExtensionSemanticVersion(1, 2, 0),
                apiRange = ExtensionApiRange(
                    ExtensionApiVersions.Current,
                    ExtensionApiVersions.Current,
                ),
                hooks = setOf(
                    ExtensionHookDeclaration(
                        hook = HostHookSpecs.BackgroundTask.hook,
                        schemaVersion = HostHookSpecs.BackgroundTask.schemaVersion,
                        requiredCapabilities = requiredCapabilities,
                    )
                ),
                capabilities = capabilities,
                backgroundTasks = listOf(
                    ExtensionBackgroundTaskDeclaration(
                        taskId = "refresh",
                        repeatIntervalHours = 24,
                        requiresNetwork = requiresNetwork,
                    )
                ),
            )
        }

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

        fun networkManifest(origins: Set<ExtensionNetworkOrigin>): ExtensionManifest =
            ExtensionManifest(
                id = EXTENSION_ID,
                displayName = "Lifecycle network extension",
                extensionVersion = ExtensionSemanticVersion(1, 1, 0),
                apiRange = ExtensionApiRange(
                    ExtensionApiVersions.Current,
                    ExtensionApiVersions.Current,
                ),
                hooks = emptySet(),
                capabilities = setOf(
                    ExtensionCapabilityRequest(
                        ExtensionCapabilityIds.Network,
                        "Reach approved test origins",
                    )
                ),
                networkOrigins = origins,
            )

        fun contributionManifest(
            extensionId: ExtensionId = EXTENSION_ID,
        ): ExtensionManifest = ExtensionManifest(
            id = extensionId,
            displayName = "Lifecycle contribution extension",
            extensionVersion = ExtensionSemanticVersion(1, 1, 0),
            apiRange = ExtensionApiRange(
                ExtensionApiVersions.Current,
                ExtensionApiVersions.Current,
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
                    ExtensionCapabilityIds.MetadataWrite,
                    "Enrich imported channel metadata",
                )
            ),
        )

        fun allContributionManifest(): ExtensionManifest = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Lifecycle contribution extension",
            extensionVersion = ExtensionSemanticVersion(1, 1, 0),
            apiRange = ExtensionApiRange(
                ExtensionApiVersions.Current,
                ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.MetadataEnrichment.hook,
                    schemaVersion = HostHookSpecs.MetadataEnrichment.schemaVersion,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.MetadataWrite),
                ),
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.EpgRefresh.hook,
                    schemaVersion = HostHookSpecs.EpgRefresh.schemaVersion,
                    requiredCapabilities = setOf(ExtensionCapabilityIds.EpgRead),
                ),
            ),
            capabilities = setOf(
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.MetadataWrite,
                    "Enrich imported channel metadata",
                ),
                ExtensionCapabilityRequest(
                    ExtensionCapabilityIds.EpgRead,
                    "Refresh programme data",
                ),
            ),
        )

        fun epgContributionManifest(): ExtensionManifest = ExtensionManifest(
            id = EXTENSION_ID,
            displayName = "Lifecycle EPG contribution extension",
            extensionVersion = ExtensionSemanticVersion(1, 1, 0),
            apiRange = ExtensionApiRange(
                ExtensionApiVersions.Current,
                ExtensionApiVersions.Current,
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
                    ExtensionCapabilityIds.EpgRead,
                    "Refresh programme data",
                )
            ),
        )
    }
}

private class RecordingContributionScheduler(
    private val enqueueFailure: Exception? = null,
) : ExtensionContributionScheduler {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()

    override suspend fun enqueue(playlistUrl: String) {
        enqueueFailure?.let { throw it }
        enqueued += playlistUrl
    }

    override suspend fun cancel(playlistUrl: String) {
        cancelled += playlistUrl
    }
}

private class RecordingBackgroundWorkOperations(
    private val existingTags: List<Set<String>> = emptyList(),
    private val enqueueFailure: Exception? = null,
) : ExtensionBackgroundWorkOperations {
    val enqueued = mutableListOf<String>()
    val cancelledUniqueWork = mutableListOf<String>()
    val cancelledTags = mutableListOf<String>()

    override suspend fun scheduledWorkTags(tag: String): List<Set<String>> = existingTags

    override suspend fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ) {
        enqueueFailure?.let { failure -> throw failure }
        enqueued += uniqueWorkName
    }

    override suspend fun cancelUniqueWork(uniqueWorkName: String) {
        cancelledUniqueWork += uniqueWorkName
    }

    override suspend fun cancelAllWorkByTag(tag: String) {
        cancelledTags += tag
    }
}
