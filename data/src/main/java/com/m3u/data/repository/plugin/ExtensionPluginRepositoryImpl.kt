package com.m3u.data.repository.plugin

import android.content.Context
import android.os.SystemClock
import androidx.work.WorkManager
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.get
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.extension.security.ActiveExtensionPrincipalRegistry
import com.m3u.data.extension.security.ExtensionOwnerIdentity
import com.m3u.data.extension.security.ProviderAccountOwnerStore
import com.m3u.data.extension.security.ProviderBrokerScopeStore
import com.m3u.data.extension.security.toPrincipal
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.extension.ExtensionContributionScheduler
import com.m3u.data.repository.extension.ExtensionSettingStore
import com.m3u.data.repository.extension.ExtensionSettingsRepository
import com.m3u.data.worker.ExtensionBackgroundTaskScheduler
import com.m3u.data.worker.ProviderSessionCleanupWorker
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionContractCatalog
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.Hook
import com.m3u.extension.runtime.ExtensionExecutionKind
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRegistrationToken
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.ExtensionTransportHealth
import com.m3u.extension.runtime.RegisteredExtension
import com.m3u.extension.runtime.reconcileCapabilitiesForRestore
import com.m3u.extension.transport.android.ExtensionTransportIncompatibleException
import com.m3u.extension.transport.android.ExtensionTrustStore
import com.m3u.extension.transport.android.InstalledExtensionService
import com.m3u.extension.transport.android.TrustedExtensionService
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class ExtensionPluginRepositoryImpl private constructor(
    private val discovery: ExtensionPluginDiscovery,
    private val trustStore: ExtensionTrustStore,
    private val transportConnector: ExtensionPluginTransportConnector,
    private val runtime: ExtensionRuntime,
    private val extensionSettingsRepository: ExtensionSettingsRepository,
    private val extensionSettingStore: ExtensionSettingStore,
    private val subscriptionProviderImporter: SubscriptionProviderImporter,
    private val activePrincipalRegistry: ActiveExtensionPrincipalRegistry,
    private val providerAccountOwnerStore: ProviderAccountOwnerStore,
    private val providerBrokerScopeStore: ProviderBrokerScopeStore?,
    private val settings: Settings,
    private val backgroundTaskScheduler: ExtensionBackgroundTaskScheduler?,
    private val playlistDao: PlaylistDao?,
    private val extensionContributionScheduler: ExtensionContributionScheduler?,
    private val scheduleSessionCleanup: () -> Unit,
    observeSettingsChanges: Boolean,
    private val nowElapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val connectTimeoutMillis: Long = CONNECT_TIMEOUT_MILLIS,
    private val healthCheckTimeoutMillis: Long = HEALTH_CHECK_TIMEOUT_MILLIS,
    private val maxConcurrentPluginInspections: Int =
        MAX_CONCURRENT_PLUGIN_INSPECTIONS,
    private val authorizationTokenFactory: () -> PluginAuthorizationToken =
        ::newPluginAuthorizationToken,
) : ExtensionPluginRepository {
    private val transports = ActiveExtensionTransports<ExtensionPluginTransport>()
    private val repositoryJob = SupervisorJob()
    private val repositoryScope = CoroutineScope(repositoryJob + Dispatchers.IO)
    private val lifecycleMutex = Mutex()
    private val backgroundSchedulingFailures = ConcurrentHashMap<String, String>()
    private val contributionCleanupFailures = ConcurrentHashMap<String, String>()
    private val contributionSchedulingFailures = ConcurrentHashMap<String, String>()
    private val pendingAuthorizations =
        ConcurrentHashMap<ExtensionServiceKey, PendingPluginAuthorization>()

    @Inject
    constructor(
        discovery: ExtensionPluginDiscovery,
        trustStore: ExtensionTrustStore,
        transportConnector: ExtensionPluginTransportConnector,
        runtime: ExtensionRuntime,
        extensionSettingsRepository: ExtensionSettingsRepository,
        extensionSettingStore: ExtensionSettingStore,
        subscriptionProviderImporter: SubscriptionProviderImporter,
        activePrincipalRegistry: ActiveExtensionPrincipalRegistry,
        providerAccountOwnerStore: ProviderAccountOwnerStore,
        providerBrokerScopeStore: ProviderBrokerScopeStore,
        settings: Settings,
        backgroundTaskScheduler: ExtensionBackgroundTaskScheduler,
        playlistDao: PlaylistDao,
        extensionContributionScheduler: ExtensionContributionScheduler,
        @ApplicationContext context: Context,
    ) : this(
        discovery = discovery,
        trustStore = trustStore,
        transportConnector = transportConnector,
        runtime = runtime,
        extensionSettingsRepository = extensionSettingsRepository,
        extensionSettingStore = extensionSettingStore,
        subscriptionProviderImporter = subscriptionProviderImporter,
        activePrincipalRegistry = activePrincipalRegistry,
        providerAccountOwnerStore = providerAccountOwnerStore,
        providerBrokerScopeStore = providerBrokerScopeStore,
        settings = settings,
        backgroundTaskScheduler = backgroundTaskScheduler,
        playlistDao = playlistDao,
        extensionContributionScheduler = extensionContributionScheduler,
        scheduleSessionCleanup = {
            ProviderSessionCleanupWorker.enqueueRetry(WorkManager.getInstance(context))
        },
        observeSettingsChanges = true,
    )

    init {
        require(connectTimeoutMillis > 0) {
            "Extension connection timeout must be positive"
        }
        require(healthCheckTimeoutMillis > 0) {
            "Extension health check timeout must be positive"
        }
        require(maxConcurrentPluginInspections > 0) {
            "Extension inspection concurrency must be positive"
        }
        if (observeSettingsChanges) {
            repositoryScope.launch {
                var previouslyEnabled: Boolean? = null
                settings.data
                    .map { preferences -> preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] ?: false }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        val previous = previouslyEnabled
                        previouslyEnabled = enabled
                        if (enabled) {
                            restoreEnabled()
                            if (previous == false) {
                                scheduleContributionsForEnabledExtensions()
                            }
                        } else {
                            lifecycleMutex.withLock { suspendExternalTransports() }
                        }
                    }
            }
        }
    }

    override suspend fun installedPlugins(): List<InstalledPlugin> {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) {
            pendingAuthorizations.clear()
            return emptyList()
        }
        val services = discovery.discover()
        pruneMissingServices(services)
        val installedPlugins = services.mapBounded(maxConcurrentPluginInspections) { service ->
            val storedIdentity = trustStore.trustedServices().singleOrNull { trusted ->
                trusted.packageName == service.packageName &&
                    trusted.serviceName == service.serviceName
            }
            val incompatibilityReason = service.incompatibilityReason
            val trusted = trustStore.isTrusted(service)
            val signatureChanged = trustStore.hasPinnedCertificate(service) && !trusted
            val wasEnabled = trusted && trustStore.isEnabled(service)
            if (incompatibilityReason != null) {
                lifecycleMutex.withLock {
                    if (trustStore.hasPinnedCertificate(service)) {
                        trustStore.setEnabled(service, false)
                    }
                    deactivateService(service.key)
                    extensionIdFor(service)?.let { extensionId ->
                        cancelBackgroundTasks(extensionId)
                        clearExtensionContributions(extensionId)
                        cancelContributionRefreshesIfUnused(setOf(extensionId))
                    }
                }
            } else if (signatureChanged) {
                lifecycleMutex.withLock {
                    trustStore.setEnabled(service, false)
                    deactivateService(service.key)
                    extensionIdFor(service)?.let { extensionId ->
                        cancelBackgroundTasks(extensionId)
                        clearExtensionContributions(extensionId)
                        cancelContributionRefreshesIfUnused(setOf(extensionId))
                    }
                }
            } else if (wasEnabled) {
                ensureConnected(service)
            }
            if (trusted && trustStore.isEnabled(service)) {
                probeTransportHealth(service)
            }
            val enabled = trusted && trustStore.isEnabled(service)
            val registeredExtension = registeredExtension(service)
            val registeredManifest = registeredExtension?.manifest
            val inspection = when {
                incompatibilityReason != null -> Result.failure(
                    IllegalArgumentException(incompatibilityReason)
                )
                registeredManifest != null -> Result.success(registeredManifest)
                wasEnabled && enabled -> Result.failure(
                    ExtensionInspectionUnavailableException()
                )
                else -> inspect(service)
            }
            val manifest = inspection.getOrNull()
            val inspectionFailure = inspection.exceptionOrNull()
            pendingAuthorizations.remove(service.key)
            val authorizationToken = manifest?.let { inspectedManifest ->
                issueAuthorization(service, inspectedManifest)
            }
            val extensionId = manifest?.id?.value ?: trustStore.extensionId(service)
            val runtimeState = registeredExtension?.state
            val grantedCapabilities = trustStore.grantedCapabilities(service)
            val networkOrigins = manifest?.canonicalNetworkOrigins()
                ?: storedIdentity?.networkOrigins.orEmpty()
            InstalledPlugin(
                packageName = service.packageName,
                serviceName = service.serviceName,
                certificateSha256 = service.certificateSha256,
                previousCertificateSha256 = storedIdentity
                    ?.certificateSha256
                    ?.takeIf { certificate -> certificate != service.certificateSha256 },
                trusted = trusted,
                signatureChanged = signatureChanged,
                extensionId = extensionId,
                enabled = enabled,
                state = when {
                    incompatibilityReason != null -> ExtensionState.INCOMPATIBLE
                    inspectionFailure is ExtensionInspectionIncompatibleException ->
                        ExtensionState.INCOMPATIBLE
                    enabled && runtimeState != null -> runtimeState
                    inspectionFailure is ExtensionInspectionUnavailableException ->
                        ExtensionState.UNHEALTHY
                    enabled -> ExtensionState.UNHEALTHY
                    else -> ExtensionState.DISABLED
                },
                displayName = manifest?.displayName ?: trustStore.displayName(service),
                version = manifest?.extensionVersion?.toString() ?: trustStore.version(service),
                developer = manifest?.metadata?.get("developer") ?: trustStore.developer(service),
                requestedCapabilities = manifest?.capabilities
                    ?.mapTo(mutableSetOf()) { request -> request.capability.id }
                    ?: trustStore.grantedCapabilities(service),
                grantedCapabilities = grantedCapabilities,
                capabilityPermissions = manifest?.capabilities
                    ?.sortedBy { request -> request.capability.id }
                    ?.map { request ->
                        PluginCapabilityPermission(
                            id = request.capability.id,
                            reason = request.reason,
                            required = request.required,
                            granted = request.capability.id in grantedCapabilities,
                        )
                    }
                    .orEmpty(),
                inspectionError = incompatibilityReason ?: when (inspectionFailure) {
                    is ExtensionInspectionIncompatibleException ->
                        "Extension manifest is incompatible"
                    is ExtensionInspectionUnavailableException ->
                        "Extension service is unavailable"
                    null -> null
                    else -> "Extension service is unavailable"
                },
                installed = true,
                canClearData = extensionId?.let { id ->
                    trustStore.isSoleTrustedOwner(service, id)
                } == true,
                networkOrigins = networkOrigins,
                approvedNetworkOrigins = if (trusted) {
                    trustStore.approvedNetworkOrigins(service)
                } else {
                    storedIdentity?.networkOrigins.orEmpty()
                },
                networkOriginSettingFields = manifest?.networkOriginSettingFields().orEmpty(),
                authorizationToken = authorizationToken,
            )
        }
        val installedServiceKeys = services.mapTo(mutableSetOf(), InstalledExtensionService::key)
        val missingPlugins = trustStore.trustedServices()
            .filter { record ->
                ExtensionServiceKey(record.packageName, record.serviceName) !in installedServiceKeys
            }
            .map { record ->
                InstalledPlugin(
                    packageName = record.packageName,
                    serviceName = record.serviceName,
                    certificateSha256 = record.certificateSha256,
                    previousCertificateSha256 = null,
                    trusted = true,
                    signatureChanged = false,
                    extensionId = record.extensionId,
                    enabled = false,
                    state = ExtensionState.DISABLED,
                    displayName = record.displayName,
                    version = record.version,
                    developer = record.developer,
                    requestedCapabilities = record.capabilities,
                    grantedCapabilities = record.capabilities,
                    capabilityPermissions = emptyList(),
                    inspectionError = null,
                    installed = false,
                    canClearData = false,
                    networkOrigins = record.networkOrigins,
                    approvedNetworkOrigins = record.networkOrigins,
                )
            }
        return (installedPlugins + missingPlugins).sortedWith(
            compareBy(InstalledPlugin::packageName, InstalledPlugin::serviceName)
        )
    }

    override suspend fun enable(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ): PluginEnableResult {
        val result = lifecycleMutex.withLock {
            enableLocked(
                packageName = packageName,
                serviceName = serviceName,
                authorizationToken = authorizationToken,
            )
        }
        return completeExplicitEnable(
            packageName = packageName,
            serviceName = serviceName,
            result = result,
            disabledResultIsSuccessful = false,
        )
    }

    override suspend fun reauthorize(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ): PluginEnableResult {
        val result = lifecycleMutex.withLock {
            if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) {
                pendingAuthorizations.remove(ExtensionServiceKey(packageName, serviceName))
                return@withLock PluginEnableResult.Rejected("External extensions are disabled")
            }
            val service = discovery.discover().singleOrNull {
                it.packageName == packageName && it.serviceName == serviceName
            } ?: return@withLock PluginEnableResult.Rejected(
                "Extension service is not installed"
            )
            service.incompatibilityReason?.let { reason ->
                return@withLock quarantine(service, reason)
            }
            val authorizedManifest = consumeAuthorization(service, authorizationToken)
                ?: return@withLock authorizationReviewRequired()
            val previousIdentity = trustStore.trustedServices().singleOrNull { trusted ->
                trusted.packageName == service.packageName &&
                    trusted.serviceName == service.serviceName
            }
            if (
                previousIdentity != null &&
                previousIdentity.certificateSha256 != service.certificateSha256
            ) {
                return@withLock repinChangedCertificate(
                    service = service,
                    previous = previousIdentity,
                    authorizedManifest = authorizedManifest,
                )
            }
            if (!trustStore.isTrusted(service)) {
                return@withLock PluginEnableResult.Rejected(
                    "Extension identity is not trusted"
                )
            }
            val previousExtensionId = trustStore.extensionId(service)
                ?: return@withLock PluginEnableResult.Rejected(
                    "Extension identity is unavailable"
                )
            val manifest = registeredExtension(service)?.manifest
                ?: inspect(service).getOrElse { error ->
                    return@withLock PluginEnableResult.Rejected(
                        "Extension inspection failed (${error.javaClass.simpleName})"
                    )
                }
            if (manifest != authorizedManifest) {
                return@withLock authorizationReviewRequired()
            }
            if (manifest.id.value != previousExtensionId) {
                return@withLock PluginEnableResult.Rejected(
                    "Extension identity changed and requires trust reset"
                )
            }
            if (trustStore.isExtensionIdClaimedByAnotherService(service, manifest.id.value)) {
                trustStore.setEnabled(service, false)
                deactivateService(service.key)
                return@withLock PluginEnableResult.Rejected(
                    "Extension ID is already trusted for another service"
                )
            }
            val wasEnabled = trustStore.isEnabled(service)
            val granted = manifest.capabilities.mapNotNullTo(mutableSetOf()) { request ->
                request.capability.id.takeIf {
                    request.capability in ExtensionContractCatalog.SupportedCapabilities
                }
            }
            trustStore.trust(
                service = service,
                extensionId = manifest.id.value,
                capabilities = granted,
                displayName = manifest.displayName,
                version = manifest.extensionVersion.toString(),
                developer = manifest.metadata["developer"],
                networkOrigins = manifest.canonicalNetworkOrigins(),
                enabled = wasEnabled,
            )
            PluginEnableResult.Enabled(manifest)
        }
        return completeExplicitEnable(
            packageName = packageName,
            serviceName = serviceName,
            result = result,
            disabledResultIsSuccessful = true,
        )
    }

    private suspend fun enableLocked(
        packageName: String,
        serviceName: String,
        authorizationToken: PluginAuthorizationToken,
    ): PluginEnableResult {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) {
            pendingAuthorizations.remove(ExtensionServiceKey(packageName, serviceName))
            return PluginEnableResult.Rejected("External extensions are disabled")
        }
        val service = discovery.discover().singleOrNull {
            it.packageName == packageName && it.serviceName == serviceName
        } ?: return PluginEnableResult.Rejected("Extension service is not installed")
        service.incompatibilityReason?.let { reason ->
            return quarantine(service, reason)
        }
        val authorizedManifest = consumeAuthorization(service, authorizationToken)
            ?: return authorizationReviewRequired()
        return enableServiceLocked(
            service = service,
            reauthorizeCapabilities = true,
            authorizedManifest = authorizedManifest,
        )
    }

    private suspend fun enableServiceLocked(
        service: InstalledExtensionService,
        reauthorizeCapabilities: Boolean,
        authorizedManifest: ExtensionManifest? = null,
        preconnectedTransport: ExtensionPluginTransport? = null,
    ): PluginEnableResult {
        var ownedTransport = preconnectedTransport
        var uncommittedExtensionId: ExtensionId? = null
        var uncommittedPrincipalExtensionId: ExtensionId? = null
        try {
            service.incompatibilityReason?.let { reason ->
                return quarantine(service, reason)
            }
            if (trustStore.hasPinnedCertificate(service) && !trustStore.isTrusted(service)) {
                return PluginEnableResult.Rejected("Extension signing certificate changed")
            }
            if (!reauthorizeCapabilities && !isAutomaticConnectionAllowed(service)) {
                deactivateService(service.key)
                return PluginEnableResult.Rejected("Extension is no longer trusted and enabled")
            }
            return runCatching {
                val transport = preconnectedTransport ?: withTimeoutOrNull(connectTimeoutMillis) {
                    transportConnector.connect(service)
                }?.also { connected -> ownedTransport = connected }
                    ?: return@runCatching PluginEnableResult.Rejected(
                        "Extension connection failed (Timeout)"
                    )
                val manifest = transport.manifest
                if (authorizedManifest != null && manifest != authorizedManifest) {
                    return@runCatching authorizationReviewRequired()
                }
                val previousExtensionId = trustStore.extensionId(service)
                if (previousExtensionId != null && previousExtensionId != manifest.id.value) {
                    trustStore.setEnabled(service, false)
                    deactivateService(service.key)
                    return@runCatching PluginEnableResult.Rejected(
                        "Extension identity changed and requires trust reset"
                    )
                }
                val renamedIdentity = trustStore.trustedServices().singleOrNull { trusted ->
                    trusted.extensionId == manifest.id.value &&
                        (
                            trusted.packageName != service.packageName ||
                                trusted.serviceName != service.serviceName
                        )
                }
                if (
                    renamedIdentity != null &&
                    reauthorizeCapabilities &&
                    renamedIdentity.packageName == service.packageName &&
                    renamedIdentity.certificateSha256 == service.certificateSha256
                ) {
                    transferTrustedIdentity(
                        previous = renamedIdentity,
                        replacement = service,
                        extensionId = manifest.id,
                    )
                    trustStore.revoke(
                        renamedIdentity.packageName,
                        renamedIdentity.serviceName,
                    )
                } else if (
                    trustStore.isExtensionIdClaimedByAnotherService(
                        service,
                        manifest.id.value,
                    )
                ) {
                    if (trustStore.isTrusted(service)) trustStore.setEnabled(service, false)
                    deactivateService(service.key)
                    return@runCatching PluginEnableResult.Rejected(
                        "Extension ID is already trusted for another service"
                    )
                }
                val previousGrants = trustStore.grantedCapabilities(service)
                val requestedCapabilities = manifest.capabilities
                    .mapNotNullTo(mutableSetOf()) { request ->
                        request.capability.id.takeIf {
                            request.capability in ExtensionContractCatalog.SupportedCapabilities
                        }
                    }
                if (!reauthorizeCapabilities) {
                    if (
                        reconcileCapabilitiesForRestore(manifest, previousGrants)
                            .requiresReauthorization
                    ) {
                        trustStore.setEnabled(service, false)
                        cancelBackgroundTasks(manifest.id.value)
                        deactivateService(service.key)
                        clearExtensionContributions(manifest.id.value)
                        cancelContributionRefreshesIfUnused(setOf(manifest.id.value))
                        return@runCatching PluginEnableResult.Rejected(
                            "New required capabilities need user approval"
                        )
                    }
                    if (!isAutomaticConnectionAllowed(service)) {
                        deactivateService(service.key)
                        return@runCatching PluginEnableResult.Rejected(
                            "Extension was disabled or revoked while reconnecting"
                        )
                    }
                }
                deactivateService(service.key)
                when (val registration = runtime.register(transport)) {
                    is ExtensionRegistrationResult.Registered -> {
                        uncommittedExtensionId = registration.extension.manifest.id
                        if (
                            authorizedManifest != null &&
                            registration.extension.manifest != authorizedManifest
                        ) {
                            runtime.unregister(registration.extension.manifest.id)
                            uncommittedExtensionId = null
                            return@runCatching authorizationReviewRequired()
                        }
                        val capabilities = if (reauthorizeCapabilities) {
                            requestedCapabilities
                        } else {
                            reconcileCapabilitiesForRestore(manifest, previousGrants).granted
                        }
                        if (reauthorizeCapabilities) {
                            trustStore.trust(
                                service = service,
                                extensionId = registration.extension.manifest.id.value,
                                capabilities = capabilities,
                                displayName = registration.extension.manifest.displayName,
                                version =
                                    registration.extension.manifest.extensionVersion.toString(),
                                developer = registration.extension.manifest.metadata["developer"],
                                networkOrigins =
                                    registration.extension.manifest.canonicalNetworkOrigins(),
                                enabled = true,
                            )
                        } else {
                            trustStore.updateTrustedManifest(
                                service = service,
                                extensionId = registration.extension.manifest.id.value,
                                capabilities = capabilities,
                                displayName = registration.extension.manifest.displayName,
                                version =
                                    registration.extension.manifest.extensionVersion.toString(),
                                developer = registration.extension.manifest.metadata["developer"],
                            )
                        }
                        val principal = service.toPrincipal(registration.extension.manifest.id)
                        if (reauthorizeCapabilities) {
                            runtime.setEnabled(registration.extension.manifest.id, true)
                        }
                        runtime.recordTransportHealth(
                            extensionId = registration.extension.manifest.id,
                            registrationToken = checkNotNull(registration.registrationToken),
                            health = ExtensionTransportHealth.UNAVAILABLE,
                        )
                        runCatching { activePrincipalRegistry.activate(principal) }
                            .getOrElse { error ->
                                runtime.unregister(registration.extension.manifest.id)
                                uncommittedExtensionId = null
                                return@runCatching PluginEnableResult.Rejected(
                                    "Extension identity could not be activated " +
                                        "(${error.javaClass.simpleName})"
                                )
                            }
                        uncommittedPrincipalExtensionId = principal.extensionId
                        val replaced = transports.put(
                            key = service.key,
                            extensionId = registration.extension.manifest.id.value,
                            transport = transport,
                            registrationToken = checkNotNull(registration.registrationToken),
                        )
                        ownedTransport = null
                        uncommittedExtensionId = null
                        uncommittedPrincipalExtensionId = null
                        replaced?.transport?.close()
                        if (!reauthorizeCapabilities && !isAutomaticConnectionAllowed(service)) {
                            deactivateService(service.key)
                            PluginEnableResult.Rejected(
                                "Extension was disabled or revoked while reconnecting"
                            )
                        } else {
                            PluginEnableResult.Enabled(registration.extension.manifest)
                        }
                    }
                    is ExtensionRegistrationResult.Rejected -> {
                        if (
                            trustStore.isTrusted(service) &&
                            trustStore.extensionId(service) == manifest.id.value
                        ) {
                            trustStore.setEnabled(service, false)
                            cancelBackgroundTasks(manifest.id.value)
                            clearExtensionContributions(manifest.id.value)
                            cancelContributionRefreshesIfUnused(setOf(manifest.id.value))
                        }
                        PluginEnableResult.Rejected(registration.error.message)
                    }
                }
            }.getOrElse { error ->
                if (error is CancellationException) throw error
                if (error is ExtensionTransportIncompatibleException) {
                    return quarantine(service, "Extension transport is incompatible")
                }
                PluginEnableResult.Rejected(
                    "Extension connection failed (${error.javaClass.simpleName})"
                )
            }
        } finally {
            ownedTransport?.let { transport ->
                uncommittedPrincipalExtensionId?.let { extensionId ->
                    activePrincipalRegistry.deactivate(
                        extensionId = extensionId,
                        packageName = service.packageName,
                        serviceName = service.serviceName,
                    )
                }
                uncommittedExtensionId?.let { extensionId ->
                    runtime.unregister(extensionId)
                }
                transport.close()
            }
        }
    }

    private suspend fun repinChangedCertificate(
        service: InstalledExtensionService,
        previous: TrustedExtensionService,
        authorizedManifest: ExtensionManifest,
    ): PluginEnableResult {
        val previousExtensionId = previous.extensionId
            ?: return PluginEnableResult.Rejected("Extension identity is unavailable")
        val manifest = inspect(service).getOrElse { error ->
            return PluginEnableResult.Rejected(
                "Extension inspection failed (${error.javaClass.simpleName})"
            )
        }
        if (manifest != authorizedManifest) {
            return authorizationReviewRequired()
        }
        if (manifest.id.value != previousExtensionId) {
            return PluginEnableResult.Rejected(
                "Extension identity changed and requires trust reset"
            )
        }
        if (trustStore.isExtensionIdClaimedByAnotherService(service, previousExtensionId)) {
            return PluginEnableResult.Rejected(
                "Extension ID is already trusted for another service"
            )
        }
        transferTrustedIdentity(
            previous = previous,
            replacement = service,
            extensionId = manifest.id,
        )
        val requestedCapabilities = manifest.capabilities.mapNotNullTo(mutableSetOf()) { request ->
            request.capability.id.takeIf {
                request.capability in ExtensionContractCatalog.SupportedCapabilities
            }
        }
        val retainedCapabilities = previous.capabilities intersect requestedCapabilities
        val retainedNetworkOrigins =
            previous.networkOrigins intersect manifest.canonicalNetworkOrigins()
        trustStore.trust(
            service = service,
            extensionId = manifest.id.value,
            capabilities = retainedCapabilities,
            displayName = manifest.displayName,
            version = manifest.extensionVersion.toString(),
            developer = manifest.metadata["developer"],
            networkOrigins = retainedNetworkOrigins,
            enabled = previous.enabled,
        )
        return if (previous.enabled) {
            enableServiceLocked(
                service = service,
                reauthorizeCapabilities = false,
                authorizedManifest = authorizedManifest,
            )
        } else {
            PluginEnableResult.Enabled(manifest)
        }
    }

    private suspend fun transferTrustedIdentity(
        previous: TrustedExtensionService,
        replacement: InstalledExtensionService,
        extensionId: ExtensionId,
    ) {
        require(previous.extensionId == extensionId.value)
        deactivateService(
            ExtensionServiceKey(previous.packageName, previous.serviceName)
        )
        activePrincipalRegistry.deactivate(
            extensionId = extensionId,
            packageName = previous.packageName,
            serviceName = previous.serviceName,
        )?.let { principal -> providerBrokerScopeStore?.closeAll(principal) }
        activePrincipalRegistry.awaitPersistence(extensionId)
        providerAccountOwnerStore.transfer(
            previous = ExtensionOwnerIdentity(
                extensionId = extensionId.value,
                packageName = previous.packageName,
                serviceName = previous.serviceName,
                certificateSha256 = previous.certificateSha256,
            ),
            replacement = ExtensionOwnerIdentity(
                extensionId = extensionId.value,
                packageName = replacement.packageName,
                serviceName = replacement.serviceName,
                certificateSha256 = replacement.certificateSha256,
            ),
        )
    }

    override suspend fun disable(extensionId: String): Boolean = lifecycleMutex.withLock {
        val services = discovery.discover().filter { trustStore.extensionId(it) == extensionId }
        services.forEach { service ->
            pendingAuthorizations.remove(service.key)
            trustStore.setEnabled(service, false)
        }
        val removed = deactivateExtension(extensionId)
        services.forEach { service ->
            activePrincipalRegistry.deactivate(
                extensionId = ExtensionId(extensionId),
                packageName = service.packageName,
                serviceName = service.serviceName,
            )?.let { principal -> providerBrokerScopeStore?.closeAll(principal) }
        }
        activePrincipalRegistry.awaitPersistence(ExtensionId(extensionId))
        cancelBackgroundTasks(extensionId)
        clearExtensionContributions(extensionId)
        cancelContributionRefreshesIfUnused(setOf(extensionId))
        removed || services.isNotEmpty()
    }

    override suspend fun revoke(packageName: String, serviceName: String) {
        lifecycleMutex.withLock {
            pendingAuthorizations.remove(ExtensionServiceKey(packageName, serviceName))
            val trustedService = trustStore.trustedServices().firstOrNull { record ->
                record.packageName == packageName && record.serviceName == serviceName
            }
            deactivateService(ExtensionServiceKey(packageName, serviceName))
            trustedService?.extensionId?.let { extensionId ->
                cancelBackgroundTasks(extensionId)
                activePrincipalRegistry.deactivate(
                    extensionId = ExtensionId(extensionId),
                    packageName = trustedService.packageName,
                    serviceName = trustedService.serviceName,
                )?.let { principal -> providerBrokerScopeStore?.closeAll(principal) }
                activePrincipalRegistry.awaitPersistence(ExtensionId(extensionId))
                providerAccountOwnerStore.revoke(
                    ExtensionOwnerIdentity(
                        extensionId = extensionId,
                        packageName = trustedService.packageName,
                        serviceName = trustedService.serviceName,
                        certificateSha256 = trustedService.certificateSha256,
                    )
                )
            }
            trustedService?.extensionId?.takeIf { extensionId ->
                trustStore.isSoleStoredOwner(packageName, serviceName, extensionId)
            }?.let { extensionId ->
                clearExtensionData(extensionId)
            }
            trustStore.revoke(packageName, serviceName)
            trustedService?.extensionId?.let { extensionId ->
                if (
                    trustStore.trustedServices().none { remaining ->
                        remaining.extensionId == extensionId
                    }
                ) {
                    runtime.forgetExternalState(ExtensionId(extensionId))
                }
                if (
                    trustStore.trustedServices().none { remaining ->
                        remaining.extensionId == extensionId && remaining.enabled
                    }
                ) {
                    clearExtensionContributions(extensionId)
                }
                cancelContributionRefreshesIfUnused(setOf(extensionId))
            }
        }
    }

    override suspend fun clearData(
        packageName: String,
        serviceName: String,
    ): PluginDataClearResult = lifecycleMutex.withLock {
        val service = discovery.discover().singleOrNull { installed ->
            installed.packageName == packageName && installed.serviceName == serviceName
        } ?: return@withLock PluginDataClearResult.Rejected(
            "Extension service is not installed"
        )
        val extensionId = trustStore.extensionId(service)
            ?: return@withLock PluginDataClearResult.Rejected(
                "Extension identity is unavailable"
            )
        if (!trustStore.isSoleTrustedOwner(service, extensionId)) {
            return@withLock PluginDataClearResult.Rejected(
                "Extension is not the unique trusted owner of this data"
            )
        }
        activePrincipalRegistry.invalidateAndRun(ExtensionId(extensionId)) {
            providerBrokerScopeStore?.closeAll(service.toPrincipal(ExtensionId(extensionId)))
            providerAccountOwnerStore.revoke(
                ExtensionOwnerIdentity(
                    extensionId = extensionId,
                    packageName = service.packageName,
                    serviceName = service.serviceName,
                    certificateSha256 = service.certificateSha256,
                )
            )
            clearExtensionData(extensionId)
        }
    }

    private suspend fun clearExtensionData(extensionId: String): PluginDataClearResult.Cleared {
        val id = ExtensionId(extensionId)
        val snapshot = extensionSettingStore.snapshot(extensionId)
        extensionSettingsRepository.clear(id)
        return PluginDataClearResult.Cleared(
            clearedSettingValues = snapshot.values.size,
            clearedCredentialHandles = snapshot.credentialHandles.size,
            clearedEpgSources = subscriptionProviderImporter.clearExtensionEpg(id),
            clearedMetadataOverlays =
                subscriptionProviderImporter.clearExtensionMetadata(id),
        )
    }

    override suspend fun diagnostics(extensionId: String): String? {
        val plugin = installedPlugins().singleOrNull { candidate ->
            candidate.extensionId == extensionId
        } ?: return null
        val registration = transports[
            ExtensionServiceKey(plugin.packageName, plugin.serviceName)
        ]
            ?.takeIf { active -> active.extensionId == extensionId }
            ?.let { active ->
                runtime.registeredExtensions().singleOrNull { extension ->
                    extension.manifest.id.value == active.extensionId
                }
            }
        val snapshot = extensionSettingStore.snapshot(extensionId)
        return DIAGNOSTICS_JSON.encodeToString(
            PluginDiagnostics(
                generatedAtEpochMillis = System.currentTimeMillis(),
                hostApiVersion = ExtensionApiVersions.Current.toString(),
                packageName = plugin.packageName,
                serviceName = plugin.serviceName,
                certificateSha256 = plugin.certificateSha256,
                extensionId = extensionId,
                extensionVersion = plugin.version,
                trusted = plugin.trusted,
                enabled = plugin.enabled,
                signatureChanged = plugin.signatureChanged,
                state = plugin.state.name.lowercase(),
                requestedCapabilities = plugin.requestedCapabilities.sorted(),
                grantedCapabilities = plugin.grantedCapabilities.sorted(),
                declaredNetworkOrigins = plugin.networkOrigins.sorted(),
                approvedNetworkOrigins = plugin.approvedNetworkOrigins.sorted(),
                networkOriginSettingFields = plugin.networkOriginSettingFields.sorted(),
                declaredHooks = registration?.manifest?.hooks
                    ?.map { declaration ->
                        "${declaration.hook.id}@${declaration.schemaVersion}"
                    }
                    ?.sorted()
                    .orEmpty(),
                consecutiveFailures = registration?.consecutiveFailures ?: 0,
                storedValueCount = snapshot.values.size,
                storedCredentialHandleCount = snapshot.credentialHandles.size,
                inspectionAvailable = plugin.inspectionError == null,
                backgroundSchedulingError = backgroundSchedulingFailures[extensionId],
                contributionCleanupError = contributionCleanupFailures[extensionId],
                contributionSchedulingError = contributionSchedulingFailures[extensionId],
            )
        )
    }

    override suspend fun restoreEnabled(): Int {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) {
            lifecycleMutex.withLock { suspendExternalTransports() }
            return 0
        }
        val services = discovery.discover()
        pruneMissingServices(services)
        val trustedServices = trustStore.trustedServices()
        val disabledExtensionIds = trustedServices
            .asSequence()
            .mapNotNull(TrustedExtensionService::extensionId)
            .distinct()
            .filter { extensionId ->
                trustedServices.none { trusted ->
                    trusted.extensionId == extensionId && trusted.enabled
                }
            }
            .toSet()
        disabledExtensionIds.forEach { extensionId ->
            clearExtensionContributions(extensionId)
        }
        val restored = services
            .filter { service -> trustStore.isTrusted(service) && trustStore.isEnabled(service) }
            .mapBounded(maxConcurrentPluginInspections) { service ->
                val result = ensureConnected(service)
                result is PluginEnableResult.Enabled &&
                    completeHealthyRestore(service, result.manifest)
            }
            .count { healthy -> healthy }
        cancelContributionRefreshesIfUnused(
            trustedServices.mapNotNullTo(mutableSetOf(), TrustedExtensionService::extensionId)
        )
        return restored
    }

    private suspend fun completeHealthyRestore(
        service: InstalledExtensionService,
        manifest: ExtensionManifest,
    ): Boolean {
        val probe = probeTransportHealth(service) ?: return false
        if (probe.health != ExtensionTransportHealth.HEALTHY) return false
        return lifecycleMutex.withLock {
            if (!isAutomaticConnectionAllowed(service)) return@withLock false
            if (transports[service.key]?.registrationToken !== probe.registrationToken) {
                return@withLock false
            }
            val registration = registeredExtension(service) ?: return@withLock false
            if (
                registration.manifest != manifest ||
                registration.state != ExtensionState.ENABLED
            ) {
                return@withLock false
            }
            val grantedCapabilities = trustStore.grantedCapabilities(service)
            reconcileExtensionContributionData(manifest, grantedCapabilities)
            reconcileBackgroundTasks(manifest, grantedCapabilities)
            scheduleSessionCleanupBestEffort(manifest.id.value)
            true
        }
    }

    private suspend fun suspendExternalTransports() {
        pendingAuthorizations.clear()
        val extensionIds = trustStore.trustedServices()
            .mapNotNull(TrustedExtensionService::extensionId)
            .distinct()
        transports.removeAll().forEach { active -> deactivateTransport(active) }
        extensionIds.forEach { extensionId ->
            cancelBackgroundTasks(extensionId)
            clearExtensionContributions(extensionId)
        }
        cancelContributionRefreshesIfUnused(extensionIds.toSet())
    }

    private suspend fun pruneMissingServices(discovered: List<InstalledExtensionService>) =
        lifecycleMutex.withLock {
            val installedKeys = discovered.mapTo(mutableSetOf(), InstalledExtensionService::key)
            pendingAuthorizations.keys.removeAll { key -> key !in installedKeys }
            val missingServices = trustStore.trustedServices()
                .filter { trusted ->
                    ExtensionServiceKey(trusted.packageName, trusted.serviceName) !in installedKeys
                }
            val missingExtensionIds = missingServices
                .mapNotNull(TrustedExtensionService::extensionId)
                .distinct()
            transports.removeMissing(installedKeys).forEach { active ->
                deactivateTransport(active)
            }
            missingExtensionIds.forEach { extensionId ->
                cancelBackgroundTasks(extensionId)
                clearExtensionContributions(extensionId)
            }
            cancelContributionRefreshesIfUnused(
                diagnosticExtensionIds = missingExtensionIds.toSet(),
                excludedServiceKeys = missingServices.mapTo(mutableSetOf()) { trusted ->
                    ExtensionServiceKey(trusted.packageName, trusted.serviceName)
                },
            )
        }

    private suspend fun ensureConnected(
        service: InstalledExtensionService,
    ): PluginEnableResult {
        registeredExtension(service)
            ?.takeIf { hasAvailableRegistration(service) }
            ?.let { registered ->
                return PluginEnableResult.Enabled(registered.manifest)
            }
        val prepared = prepareAutomaticConnection(service)
        var connectionConsumed = false
        try {
            return lifecycleMutex.withLock {
                registeredExtension(service)
                    ?.takeIf { hasAvailableRegistration(service) }
                    ?.let { registered ->
                        return@withLock PluginEnableResult.Enabled(registered.manifest)
                    }
                when (prepared) {
                    is AutomaticConnectionPreparation.Connected -> {
                        connectionConsumed = true
                        enableServiceLocked(
                            service = service,
                            reauthorizeCapabilities = false,
                            preconnectedTransport = prepared.transport,
                        )
                    }
                    is AutomaticConnectionPreparation.Incompatible ->
                        quarantine(service, "Extension transport is incompatible")
                    is AutomaticConnectionPreparation.Unavailable ->
                        PluginEnableResult.Rejected(prepared.reason)
                }
            }
        } finally {
            if (!connectionConsumed) prepared.closeIfConnected()
        }
    }

    private suspend fun prepareAutomaticConnection(
        service: InstalledExtensionService,
    ): AutomaticConnectionPreparation {
        if (service.incompatibilityReason != null) {
            return AutomaticConnectionPreparation.Incompatible
        }
        if (!isAutomaticConnectionAllowed(service)) {
            return AutomaticConnectionPreparation.Unavailable(
                "Extension is no longer trusted and enabled"
            )
        }
        return try {
            val transport = withTimeoutOrNull(connectTimeoutMillis) {
                transportConnector.connect(service)
            } ?: return AutomaticConnectionPreparation.Unavailable(
                "Extension connection failed (Timeout)"
            )
            AutomaticConnectionPreparation.Connected(transport)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: ExtensionTransportIncompatibleException) {
            AutomaticConnectionPreparation.Incompatible
        } catch (error: Exception) {
            AutomaticConnectionPreparation.Unavailable(
                "Extension connection failed (${error.javaClass.simpleName})"
            )
        }
    }

    private fun hasAvailableRegistration(service: InstalledExtensionService): Boolean {
        val active = transports[service.key] ?: return false
        val trustedExtensionId = trustStore.extensionId(service) ?: return false
        if (active.extensionId != trustedExtensionId || !active.transport.isConnectionAvailable) {
            return false
        }
        val registration = runtime.registeredExtensions().firstOrNull { extension ->
            extension.manifest.id.value == active.extensionId
        } ?: return false
        if (registration.state == ExtensionState.DISABLED) return false
        return true
    }

    private fun registeredExtension(
        service: InstalledExtensionService,
    ): RegisteredExtension? {
        val active = transports[service.key] ?: return null
        if (
            active.extensionId != trustStore.extensionId(service) ||
            !active.transport.isConnectionAvailable
        ) {
            return null
        }
        return runtime.registeredExtensions().firstOrNull { extension ->
            extension.manifest.id.value == active.extensionId
        }
    }

    private suspend fun probeTransportHealth(
        service: InstalledExtensionService,
    ): TransportHealthProbe? {
        val active = transports[service.key] ?: return null
        if (active.extensionId != trustStore.extensionId(service)) return null
        val registrationToken = active.registrationToken ?: return null
        val health = try {
            if (!active.transport.isConnectionAvailable) {
                ExtensionTransportHealth.UNAVAILABLE
            } else {
                withTimeoutOrNull(healthCheckTimeoutMillis) {
                    active.transport.health()
                } ?: ExtensionTransportHealth.UNAVAILABLE
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ExtensionTransportHealth.UNAVAILABLE
        }
        if (transports[service.key]?.transport !== active.transport) return null
        runtime.recordTransportHealth(
            extensionId = ExtensionId(active.extensionId),
            registrationToken = registrationToken,
            health = health,
        ) ?: return null
        return TransportHealthProbe(health, registrationToken)
    }

    private suspend fun deactivateService(key: ExtensionServiceKey): Boolean {
        val active = transports.remove(key) ?: return false
        deactivateTransport(active)
        return true
    }

    private suspend fun deactivateTransport(
        active: ActiveExtensionTransport<ExtensionPluginTransport>,
    ) {
        val extensionId = ExtensionId(active.extensionId)
        val principal = activePrincipalRegistry.deactivate(
            extensionId = extensionId,
            packageName = active.serviceKey.packageName,
            serviceName = active.serviceKey.serviceName,
        )
        principal?.let { providerBrokerScopeStore?.closeAll(it) }
        runtime.unregister(extensionId)
        active.transport.close()
        activePrincipalRegistry.awaitPersistence(extensionId)
    }

    private suspend fun deactivateExtension(extensionId: String): Boolean {
        val removed = transports.removeByExtensionId(extensionId)
        removed.forEach { active ->
            val principal = activePrincipalRegistry.deactivate(
                extensionId = ExtensionId(extensionId),
                packageName = active.serviceKey.packageName,
                serviceName = active.serviceKey.serviceName,
            )
            principal?.let { providerBrokerScopeStore?.closeAll(it) }
            active.transport.close()
        }
        if (removed.isNotEmpty()) runtime.unregister(ExtensionId(extensionId))
        if (removed.isNotEmpty()) {
            activePrincipalRegistry.awaitPersistence(ExtensionId(extensionId))
        }
        return removed.isNotEmpty()
    }

    private suspend fun isAutomaticConnectionAllowed(service: InstalledExtensionService): Boolean =
        settings[PreferencesKeys.EXTERNAL_EXTENSIONS] &&
            trustStore.isTrusted(service) &&
            trustStore.isEnabled(service)

    private suspend fun quarantine(
        service: InstalledExtensionService,
        reason: String,
    ): PluginEnableResult.Rejected {
        pendingAuthorizations.remove(service.key)
        if (trustStore.hasPinnedCertificate(service)) {
            trustStore.setEnabled(service, false)
        }
        deactivateService(service.key)
        extensionIdFor(service)?.let { extensionId ->
            cancelBackgroundTasks(extensionId)
            clearExtensionContributions(extensionId)
            cancelContributionRefreshesIfUnused(setOf(extensionId))
        }
        return PluginEnableResult.Rejected(reason)
    }

    private fun extensionIdFor(service: InstalledExtensionService): String? =
        trustStore.extensionId(service)

    private suspend fun cancelBackgroundTasks(extensionId: String) {
        try {
            backgroundTaskScheduler?.cancel(ExtensionId(extensionId))
            backgroundSchedulingFailures.remove(extensionId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            backgroundSchedulingFailures[extensionId] = error.javaClass.simpleName
        }
    }

    private suspend fun clearExtensionContributions(extensionId: String) {
        maintainExtensionContributionData(
            extensionId = ExtensionId(extensionId),
            clearMetadata = true,
            clearEpg = true,
        )
    }

    private suspend fun reconcileExtensionContributionData(
        manifest: ExtensionManifest,
        grantedCapabilities: Set<String>,
    ) {
        maintainExtensionContributionData(
            extensionId = manifest.id,
            clearMetadata = !manifest.hasAuthorizedContributionHook(
                hookId = ExtensionHookIds.MetadataChannelEnrich,
                capabilityId = ExtensionCapabilityIds.MetadataWrite.id,
                grantedCapabilities = grantedCapabilities,
            ),
            clearEpg = !manifest.hasAuthorizedContributionHook(
                hookId = ExtensionHookIds.EpgContentRefresh,
                capabilityId = ExtensionCapabilityIds.EpgRead.id,
                grantedCapabilities = grantedCapabilities,
            ),
        )
    }

    private suspend fun maintainExtensionContributionData(
        extensionId: ExtensionId,
        clearMetadata: Boolean,
        clearEpg: Boolean,
    ) {
        if (!clearMetadata && !clearEpg) return
        var firstFailure: Exception? = null
        if (clearMetadata) {
            try {
                subscriptionProviderImporter.clearExtensionMetadata(extensionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                firstFailure = error
            }
        }
        if (clearEpg) {
            try {
                subscriptionProviderImporter.clearExtensionEpg(extensionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (firstFailure == null) firstFailure = error
            }
        }
        val failureName = firstFailure?.javaClass?.simpleName
        if (failureName == null) {
            contributionCleanupFailures.remove(extensionId.value)
        } else {
            contributionCleanupFailures[extensionId.value] = failureName
        }
    }

    private suspend fun reconcileBackgroundTasks(
        manifest: ExtensionManifest,
        grantedCapabilities: Set<String>,
    ) {
        try {
            backgroundTaskScheduler?.reconcile(
                manifest = manifest,
                enabled = true,
                grantedCapabilities = grantedCapabilities,
            )
            backgroundSchedulingFailures.remove(manifest.id.value)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            backgroundSchedulingFailures[manifest.id.value] = error.javaClass.simpleName
        }
    }

    private fun scheduleSessionCleanupBestEffort(extensionId: String) {
        try {
            scheduleSessionCleanup()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            backgroundSchedulingFailures[extensionId] = error.javaClass.simpleName
        }
    }

    private suspend fun completeExplicitEnable(
        packageName: String,
        serviceName: String,
        result: PluginEnableResult,
        disabledResultIsSuccessful: Boolean,
    ): PluginEnableResult {
        if (result !is PluginEnableResult.Enabled) return result
        val extensionId = result.manifest.id.value
        val service = discovery.discover().singleOrNull { candidate ->
            candidate.packageName == packageName && candidate.serviceName == serviceName
        } ?: return PluginEnableResult.Rejected("Extension service is not installed")
        if (!trustStore.isTrusted(service)) {
            return PluginEnableResult.Rejected("Extension identity is not trusted")
        }
        if (!trustStore.isEnabled(service)) {
            clearExtensionContributions(extensionId)
            cancelContributionRefreshesIfUnused(setOf(extensionId))
            return if (disabledResultIsSuccessful) {
                result
            } else {
                PluginEnableResult.Rejected("Extension was disabled while enabling")
            }
        }
        val probe = probeTransportHealth(service)
        if (probe?.health != ExtensionTransportHealth.HEALTHY) {
            return PluginEnableResult.Rejected("Extension health check failed")
        }
        return try {
            lifecycleMutex.withLock {
                if (!isAutomaticConnectionAllowed(service)) {
                    clearExtensionContributions(extensionId)
                    cancelContributionRefreshesIfUnused(setOf(extensionId))
                    return@withLock if (disabledResultIsSuccessful) {
                        result
                    } else {
                        PluginEnableResult.Rejected("Extension was disabled while enabling")
                    }
                }
                if (transports[service.key]?.registrationToken !== probe.registrationToken) {
                    return@withLock PluginEnableResult.Rejected(
                        "Extension changed while enabling"
                    )
                }
                val registration = registeredExtension(service)
                if (
                    registration == null ||
                    registration.manifest.id != result.manifest.id ||
                    registration.state != ExtensionState.ENABLED
                ) {
                    return@withLock PluginEnableResult.Rejected(
                        "Extension health check failed"
                    )
                }
                val grantedCapabilities = trustStore.grantedCapabilities(service)
                reconcileExtensionContributionData(result.manifest, grantedCapabilities)
                reconcileBackgroundTasks(result.manifest, grantedCapabilities)
                scheduleSessionCleanupBestEffort(extensionId)
                if (result.manifest.hasAuthorizedContributionHook(grantedCapabilities)) {
                    scheduleContributionRefreshes(setOf(extensionId))
                } else {
                    cancelContributionRefreshesIfUnused(setOf(extensionId))
                }
                result
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            contributionSchedulingFailures[extensionId] = error.javaClass.simpleName
            PluginEnableResult.Rejected("Extension activation failed")
        }
    }

    private suspend fun scheduleContributionsForEnabledExtensions() {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) return
        val extensionIds = runtime.registeredExtensions()
            .asSequence()
            .filter { extension ->
                extension.executionKind == ExtensionExecutionKind.EXTERNAL &&
                    extension.state == ExtensionState.ENABLED
            }
            .filter { extension ->
                extension.manifest.hasAuthorizedContributionHook(
                    trustStore.grantedCapabilities(extension.manifest.id.value)
                )
            }
            .mapTo(linkedSetOf()) { extension -> extension.manifest.id.value }
        scheduleContributionRefreshes(extensionIds)
    }

    private suspend fun scheduleContributionRefreshes(extensionIds: Set<String>) {
        if (extensionIds.isEmpty()) return
        val dao = playlistDao ?: return
        val scheduler = extensionContributionScheduler ?: return
        var firstFailure: Exception? = null
        val playlistUrls = try {
            dao.getAll()
                .asSequence()
                .filter { playlist -> playlist.source != DataSource.EPG }
                .map { playlist -> playlist.url }
                .filter(String::isNotBlank)
                .distinct()
                .toList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            firstFailure = error
            emptyList()
        }
        playlistUrls.forEach { playlistUrl ->
            try {
                scheduler.enqueue(playlistUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (firstFailure == null) firstFailure = error
            }
        }
        val failureName = firstFailure?.javaClass?.simpleName
        extensionIds.forEach { extensionId ->
            if (failureName == null) {
                contributionSchedulingFailures.remove(extensionId)
            } else {
                contributionSchedulingFailures[extensionId] = failureName
            }
        }
    }

    private suspend fun cancelContributionRefreshesIfUnused(
        diagnosticExtensionIds: Set<String>,
        excludedServiceKeys: Set<ExtensionServiceKey> = emptySet(),
    ) {
        val hasContributor = try {
            hasPotentialAuthorizedContributionExtension(excludedServiceKeys)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            diagnosticExtensionIds.forEach { extensionId ->
                contributionSchedulingFailures[extensionId] = error.javaClass.simpleName
            }
            return
        }
        if (hasContributor) return
        val dao = playlistDao ?: return
        val scheduler = extensionContributionScheduler ?: return
        var firstFailure: Exception? = null
        val playlistUrls = try {
            dao.getAll()
                .asSequence()
                .filter { playlist -> playlist.source != DataSource.EPG }
                .map { playlist -> playlist.url }
                .filter(String::isNotBlank)
                .distinct()
                .toList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            firstFailure = error
            emptyList()
        }
        playlistUrls.forEach { playlistUrl ->
            try {
                scheduler.cancel(playlistUrl)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (firstFailure == null) firstFailure = error
            }
        }
        val failureName = firstFailure?.javaClass?.simpleName
        diagnosticExtensionIds.forEach { extensionId ->
            if (failureName == null) {
                contributionSchedulingFailures.remove(extensionId)
            } else {
                contributionSchedulingFailures[extensionId] = failureName
            }
        }
    }

    private suspend fun hasPotentialAuthorizedContributionExtension(
        excludedServiceKeys: Set<ExtensionServiceKey> = emptySet(),
    ): Boolean {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) return false
        val registeredExtensions = runtime.registeredExtensions()
            .asSequence()
            .filter { extension ->
                extension.executionKind == ExtensionExecutionKind.EXTERNAL &&
                    extension.state == ExtensionState.ENABLED
            }
            .associateBy { extension -> extension.manifest.id.value }
        return trustStore.trustedServices().any { trusted ->
            if (
                ExtensionServiceKey(trusted.packageName, trusted.serviceName) in
                excludedServiceKeys
            ) {
                return@any false
            }
            val extensionId = trusted.extensionId
            if (!trusted.enabled || extensionId == null) return@any false
            val registered = registeredExtensions[extensionId]
            if (registered != null) {
                registered.manifest.hasAuthorizedContributionHook(trusted.capabilities)
            } else {
                ExtensionCapabilityIds.MetadataWrite.id in trusted.capabilities ||
                    ExtensionCapabilityIds.EpgRead.id in trusted.capabilities
            }
        }
    }

    private suspend fun inspect(service: InstalledExtensionService): Result<ExtensionManifest> {
        service.incompatibilityReason?.let { reason ->
            return Result.failure(ExtensionInspectionIncompatibleException(reason))
        }
        val transport = runCatching {
            withTimeoutOrNull(connectTimeoutMillis) {
                transportConnector.connect(service)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error.toInspectionFailure())
        } ?: return Result.failure(
            ExtensionInspectionUnavailableException("Extension connection timed out")
        )
        return runCatching {
            try {
                val manifest = transport.manifest
                runtime.validateExternalManifest(manifest)?.let { error ->
                    throw ExtensionInspectionIncompatibleException(error.message)
                }
                manifest
            } finally {
                transport.close()
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
    }

    internal suspend fun shutdownForTest() {
        repositoryJob.cancelAndJoin()
    }

    companion object {
        internal fun createForTest(
            discovery: ExtensionPluginDiscovery,
            trustStore: ExtensionTrustStore,
            transportConnector: ExtensionPluginTransportConnector,
            runtime: ExtensionRuntime,
            extensionSettingsRepository: ExtensionSettingsRepository,
            extensionSettingStore: ExtensionSettingStore,
            subscriptionProviderImporter: SubscriptionProviderImporter,
            activePrincipalRegistry: ActiveExtensionPrincipalRegistry =
                ActiveExtensionPrincipalRegistry(),
            providerAccountOwnerStore: ProviderAccountOwnerStore,
            providerBrokerScopeStore: ProviderBrokerScopeStore? = null,
            settings: Settings,
            backgroundTaskScheduler: ExtensionBackgroundTaskScheduler? = null,
            playlistDao: PlaylistDao? = null,
            extensionContributionScheduler: ExtensionContributionScheduler? = null,
            scheduleSessionCleanup: () -> Unit = {},
            observeSettingsChanges: Boolean = false,
            nowElapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
            connectTimeoutMillis: Long = CONNECT_TIMEOUT_MILLIS,
            healthCheckTimeoutMillis: Long = HEALTH_CHECK_TIMEOUT_MILLIS,
            maxConcurrentPluginInspections: Int =
                MAX_CONCURRENT_PLUGIN_INSPECTIONS,
            authorizationTokenFactory: () -> PluginAuthorizationToken =
                ::newPluginAuthorizationToken,
        ): ExtensionPluginRepositoryImpl = ExtensionPluginRepositoryImpl(
            discovery = discovery,
            trustStore = trustStore,
            transportConnector = transportConnector,
            runtime = runtime,
            extensionSettingsRepository = extensionSettingsRepository,
            extensionSettingStore = extensionSettingStore,
            subscriptionProviderImporter = subscriptionProviderImporter,
            activePrincipalRegistry = activePrincipalRegistry,
            providerAccountOwnerStore = providerAccountOwnerStore,
            providerBrokerScopeStore = providerBrokerScopeStore,
            settings = settings,
            backgroundTaskScheduler = backgroundTaskScheduler,
            playlistDao = playlistDao,
            extensionContributionScheduler = extensionContributionScheduler,
            scheduleSessionCleanup = scheduleSessionCleanup,
            observeSettingsChanges = observeSettingsChanges,
            nowElapsedRealtimeMillis = nowElapsedRealtimeMillis,
            connectTimeoutMillis = connectTimeoutMillis,
            healthCheckTimeoutMillis = healthCheckTimeoutMillis,
            maxConcurrentPluginInspections = maxConcurrentPluginInspections,
            authorizationTokenFactory = authorizationTokenFactory,
        )

        private const val CONNECT_TIMEOUT_MILLIS = 5_000L
        private const val HEALTH_CHECK_TIMEOUT_MILLIS = 2_000L
        private const val MAX_CONCURRENT_PLUGIN_INSPECTIONS = 4
        private const val AUTHORIZATION_TTL_MILLIS = 5 * 60 * 1_000L
        private val DIAGNOSTICS_JSON = Json {
            prettyPrint = true
            explicitNulls = false
        }
    }

    private fun issueAuthorization(
        service: InstalledExtensionService,
        manifest: ExtensionManifest,
    ): PluginAuthorizationToken {
        val token = authorizationTokenFactory()
        val issuedAtElapsedRealtimeMillis = nowElapsedRealtimeMillis()
        pendingAuthorizations[service.key] = PendingPluginAuthorization(
            token = token,
            certificateSha256 = service.certificateSha256,
            manifest = manifest,
            issuedAtElapsedRealtimeMillis = issuedAtElapsedRealtimeMillis,
            expiresAtElapsedRealtimeMillis =
                issuedAtElapsedRealtimeMillis + AUTHORIZATION_TTL_MILLIS,
        )
        return token
    }

    private fun consumeAuthorization(
        service: InstalledExtensionService,
        authorizationToken: PluginAuthorizationToken,
    ): ExtensionManifest? {
        val pending = pendingAuthorizations[service.key] ?: return null
        if (pending.token != authorizationToken) return null
        if (!pendingAuthorizations.remove(service.key, pending)) return null
        if (pending.certificateSha256 != service.certificateSha256) return null
        if (
            nowElapsedRealtimeMillis() !in
            pending.issuedAtElapsedRealtimeMillis..pending.expiresAtElapsedRealtimeMillis
        ) {
            return null
        }
        return pending.manifest
    }

    private fun authorizationReviewRequired(): PluginEnableResult.Rejected =
        PluginEnableResult.Rejected(
            "Extension details changed or expired; review them again before authorizing"
        )
}

private data class PendingPluginAuthorization(
    val token: PluginAuthorizationToken,
    val certificateSha256: String,
    val manifest: ExtensionManifest,
    val issuedAtElapsedRealtimeMillis: Long,
    val expiresAtElapsedRealtimeMillis: Long,
)

private data class TransportHealthProbe(
    val health: ExtensionTransportHealth,
    val registrationToken: ExtensionRegistrationToken,
)

private sealed interface AutomaticConnectionPreparation {
    data class Connected(
        val transport: ExtensionPluginTransport,
    ) : AutomaticConnectionPreparation

    data class Unavailable(
        val reason: String,
    ) : AutomaticConnectionPreparation

    data object Incompatible : AutomaticConnectionPreparation
}

private fun AutomaticConnectionPreparation.closeIfConnected() {
    if (this is AutomaticConnectionPreparation.Connected) transport.close()
}

private suspend fun <Input, Output> List<Input>.mapBounded(
    maximumConcurrency: Int,
    transform: suspend (Input) -> Output,
): List<Output> = coroutineScope {
    if (isEmpty()) return@coroutineScope emptyList()
    val nextIndex = AtomicInteger(0)
    val results = arrayOfNulls<Any>(size)
    List(minOf(size, maximumConcurrency)) {
        launch {
            while (true) {
                val index = nextIndex.getAndIncrement()
                if (index >= size) break
                results[index] = BoundedMapValue(transform(this@mapBounded[index]))
            }
        }
    }.joinAll()
    @Suppress("UNCHECKED_CAST")
    results.map { stored ->
        checkNotNull(stored as? BoundedMapValue<Output>).value
    }
}

private data class BoundedMapValue<Value>(
    val value: Value,
)

private class ExtensionInspectionIncompatibleException(
    message: String = "Extension manifest is incompatible",
    cause: Throwable? = null,
) : Exception(message, cause)

private class ExtensionInspectionUnavailableException(
    message: String = "Extension service is unavailable",
    cause: Throwable? = null,
) : Exception(message, cause)

private fun Throwable.toInspectionFailure(): Exception =
    if (this is ExtensionTransportIncompatibleException) {
        ExtensionInspectionIncompatibleException(cause = this)
    } else {
        ExtensionInspectionUnavailableException(cause = this)
    }

private val AUTHORIZATION_RANDOM = SecureRandom()
private val AUTHORIZATION_HEX = "0123456789abcdef".toCharArray()

private fun newPluginAuthorizationToken(): PluginAuthorizationToken {
    val bytes = ByteArray(32)
    AUTHORIZATION_RANDOM.nextBytes(bytes)
    return PluginAuthorizationToken(
        buildString(bytes.size * 2) {
            bytes.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(AUTHORIZATION_HEX[value ushr 4])
                append(AUTHORIZATION_HEX[value and 0x0f])
            }
        }
    )
}

@Serializable
private data class PluginDiagnostics(
    val formatVersion: Int = 3,
    val generatedAtEpochMillis: Long,
    val hostApiVersion: String,
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
    val extensionId: String,
    val extensionVersion: String?,
    val trusted: Boolean,
    val enabled: Boolean,
    val signatureChanged: Boolean,
    val state: String,
    val requestedCapabilities: List<String>,
    val grantedCapabilities: List<String>,
    val declaredNetworkOrigins: List<String>,
    val approvedNetworkOrigins: List<String>,
    val networkOriginSettingFields: List<String>,
    val declaredHooks: List<String>,
    val consecutiveFailures: Int,
    val storedValueCount: Int,
    val storedCredentialHandleCount: Int,
    val inspectionAvailable: Boolean,
    val backgroundSchedulingError: String?,
    val contributionCleanupError: String?,
    val contributionSchedulingError: String?,
)

private fun ExtensionManifest.canonicalNetworkOrigins(): Set<String> =
    networkOrigins.mapTo(mutableSetOf()) { origin -> origin.canonicalValue }

private fun ExtensionManifest.networkOriginSettingFields(): Set<String> =
    settingsSchema
        ?.fields
        ?.asSequence()
        ?.filter { field -> field.networkOrigin }
        ?.mapTo(mutableSetOf()) { field -> field.label }
        .orEmpty()

private fun ExtensionManifest.hasAuthorizedContributionHook(
    grantedCapabilities: Set<String>,
): Boolean = hasAuthorizedContributionHook(
    hookId = ExtensionHookIds.MetadataChannelEnrich,
    capabilityId = ExtensionCapabilityIds.MetadataWrite.id,
    grantedCapabilities = grantedCapabilities,
) || hasAuthorizedContributionHook(
    hookId = ExtensionHookIds.EpgContentRefresh,
    capabilityId = ExtensionCapabilityIds.EpgRead.id,
    grantedCapabilities = grantedCapabilities,
)

private fun ExtensionManifest.hasAuthorizedContributionHook(
    hookId: Hook,
    capabilityId: String,
    grantedCapabilities: Set<String>,
): Boolean = hooks.any { declaration ->
    declaration.hook == hookId &&
        capabilityId in grantedCapabilities &&
        declaration.requiredCapabilities.all { capability ->
            capability.id in grantedCapabilities
        }
}
