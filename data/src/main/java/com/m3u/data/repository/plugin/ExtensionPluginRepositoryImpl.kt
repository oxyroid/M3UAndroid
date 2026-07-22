package com.m3u.data.repository.plugin

import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.get
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.repository.extension.ExtensionSettingStore
import com.m3u.data.repository.extension.ExtensionSettingsRepository
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionContractCatalog
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.runtime.ExtensionRegistrationResult
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.runtime.RegisteredExtension
import com.m3u.extension.runtime.reconcileCapabilitiesForRestore
import com.m3u.extension.transport.android.ExtensionTrustStore
import com.m3u.extension.transport.android.InstalledExtensionService
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val settings: Settings,
    observeSettingsChanges: Boolean,
) : ExtensionPluginRepository {
    private val transports = ActiveExtensionTransports<ExtensionPluginTransport>()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()

    @Inject
    constructor(
        discovery: ExtensionPluginDiscovery,
        trustStore: ExtensionTrustStore,
        transportConnector: ExtensionPluginTransportConnector,
        runtime: ExtensionRuntime,
        extensionSettingsRepository: ExtensionSettingsRepository,
        extensionSettingStore: ExtensionSettingStore,
        subscriptionProviderImporter: SubscriptionProviderImporter,
        settings: Settings,
    ) : this(
        discovery = discovery,
        trustStore = trustStore,
        transportConnector = transportConnector,
        runtime = runtime,
        extensionSettingsRepository = extensionSettingsRepository,
        extensionSettingStore = extensionSettingStore,
        subscriptionProviderImporter = subscriptionProviderImporter,
        settings = settings,
        observeSettingsChanges = true,
    )

    init {
        if (observeSettingsChanges) {
            repositoryScope.launch {
                settings.data
                    .map { preferences -> preferences[PreferencesKeys.EXTERNAL_EXTENSIONS] ?: false }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) {
                            restoreEnabled()
                        } else {
                            lifecycleMutex.withLock { suspendExternalTransports() }
                        }
                    }
            }
        }
    }

    override suspend fun installedPlugins(): List<InstalledPlugin> {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) return emptyList()
        val services = discovery.discover()
        pruneMissingServices(services)
        val installedPlugins = services.map { service ->
            val incompatibilityReason = service.incompatibilityReason
            val trusted = trustStore.isTrusted(service)
            val signatureChanged = trustStore.hasPinnedCertificate(service) && !trusted
            if (incompatibilityReason != null) {
                lifecycleMutex.withLock {
                    if (trustStore.hasPinnedCertificate(service)) {
                        trustStore.setEnabled(service, false)
                    }
                    deactivateService(service.key)
                }
            } else if (signatureChanged) {
                lifecycleMutex.withLock {
                    trustStore.setEnabled(service, false)
                    deactivateService(service.key)
                }
            } else if (trusted && trustStore.isEnabled(service)) {
                ensureConnected(service)
            }
            val registeredExtension = registeredExtension(service)
            val registeredManifest = registeredExtension?.manifest
            val inspection = when {
                incompatibilityReason != null -> Result.failure(
                    IllegalArgumentException(incompatibilityReason)
                )
                signatureChanged -> null
                registeredManifest != null -> Result.success(registeredManifest)
                else -> inspect(service)
            }
            val manifest = inspection?.getOrNull()
            val extensionId = manifest?.id?.value ?: trustStore.extensionId(service)
            val runtimeState = registeredExtension?.state
            val grantedCapabilities = trustStore.grantedCapabilities(service)
            InstalledPlugin(
                packageName = service.packageName,
                serviceName = service.serviceName,
                certificateSha256 = service.certificateSha256,
                trusted = trusted,
                signatureChanged = signatureChanged,
                extensionId = extensionId,
                enabled = trusted && trustStore.isEnabled(service),
                state = when {
                    incompatibilityReason != null -> ExtensionState.INCOMPATIBLE
                    inspection?.isFailure == true -> ExtensionState.INCOMPATIBLE
                    trusted && trustStore.isEnabled(service) -> runtimeState ?: ExtensionState.DISABLED
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
                inspectionError = incompatibilityReason ?: inspection?.exceptionOrNull()?.let {
                    "Extension manifest is incompatible or unavailable"
                },
                installed = true,
                canClearData = extensionId?.let { id ->
                    trustStore.isSoleTrustedOwner(service, id)
                } == true,
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
                )
            }
        return (installedPlugins + missingPlugins).sortedWith(
            compareBy(InstalledPlugin::packageName, InstalledPlugin::serviceName)
        )
    }

    override suspend fun enable(packageName: String, serviceName: String): PluginEnableResult =
        lifecycleMutex.withLock {
            enableLocked(packageName, serviceName, reauthorizeCapabilities = true)
        }

    override suspend fun reauthorize(
        packageName: String,
        serviceName: String,
    ): PluginEnableResult = lifecycleMutex.withLock {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) {
            return@withLock PluginEnableResult.Rejected("External extensions are disabled")
        }
        val service = discovery.discover().singleOrNull {
            it.packageName == packageName && it.serviceName == serviceName
        } ?: return@withLock PluginEnableResult.Rejected("Extension service is not installed")
        service.incompatibilityReason?.let { reason ->
            return@withLock quarantine(service, reason)
        }
        if (!trustStore.isTrusted(service)) {
            return@withLock PluginEnableResult.Rejected("Extension identity is not trusted")
        }
        val previousExtensionId = trustStore.extensionId(service)
            ?: return@withLock PluginEnableResult.Rejected("Extension identity is unavailable")
        val manifest = registeredExtension(service)?.manifest
            ?: inspect(service).getOrElse { error ->
                return@withLock PluginEnableResult.Rejected(
                    "Extension inspection failed (${error.javaClass.simpleName})"
                )
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
        )
        if (!wasEnabled) trustStore.setEnabled(service, false)
        PluginEnableResult.Enabled(manifest)
    }

    private suspend fun enableLocked(
        packageName: String,
        serviceName: String,
        reauthorizeCapabilities: Boolean,
    ): PluginEnableResult {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) {
            return PluginEnableResult.Rejected("External extensions are disabled")
        }
        val service = discovery.discover().singleOrNull {
            it.packageName == packageName && it.serviceName == serviceName
        } ?: return PluginEnableResult.Rejected("Extension service is not installed")
        service.incompatibilityReason?.let { reason ->
            return quarantine(service, reason)
        }
        return enableServiceLocked(service, reauthorizeCapabilities)
    }

    private suspend fun enableServiceLocked(
        service: InstalledExtensionService,
        reauthorizeCapabilities: Boolean,
    ): PluginEnableResult {
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
            val transport = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
                transportConnector.connect(service)
            } ?: return@runCatching PluginEnableResult.Rejected(
                "Extension connection failed (Timeout)"
            )
            val manifest = transport.manifest
            val previousExtensionId = trustStore.extensionId(service)
            if (previousExtensionId != null && previousExtensionId != manifest.id.value) {
                trustStore.setEnabled(service, false)
                deactivateService(service.key)
                transport.close()
                return@runCatching PluginEnableResult.Rejected(
                    "Extension identity changed and requires trust reset"
                )
            }
            if (trustStore.isExtensionIdClaimedByAnotherService(service, manifest.id.value)) {
                if (trustStore.isTrusted(service)) trustStore.setEnabled(service, false)
                deactivateService(service.key)
                transport.close()
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
                    deactivateService(service.key)
                    transport.close()
                    return@runCatching PluginEnableResult.Rejected(
                        "New required capabilities need user approval"
                    )
                }
                if (!isAutomaticConnectionAllowed(service)) {
                    deactivateService(service.key)
                    transport.close()
                    return@runCatching PluginEnableResult.Rejected(
                        "Extension was disabled or revoked while reconnecting"
                    )
                }
            }
            deactivateService(service.key)
            when (val registration = runtime.register(transport)) {
                is ExtensionRegistrationResult.Registered -> {
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
                            version = registration.extension.manifest.extensionVersion.toString(),
                            developer = registration.extension.manifest.metadata["developer"],
                        )
                    } else {
                        trustStore.updateTrustedManifest(
                            service = service,
                            extensionId = registration.extension.manifest.id.value,
                            capabilities = capabilities,
                            displayName = registration.extension.manifest.displayName,
                            version = registration.extension.manifest.extensionVersion.toString(),
                            developer = registration.extension.manifest.metadata["developer"],
                        )
                    }
                    val replaced = transports.put(
                        key = service.key,
                        extensionId = registration.extension.manifest.id.value,
                        transport = transport,
                    )
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
                    transport.close()
                    PluginEnableResult.Rejected(registration.error.message)
                }
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            PluginEnableResult.Rejected("Extension connection failed (${error.javaClass.simpleName})")
        }
    }

    override suspend fun disable(extensionId: String): Boolean = lifecycleMutex.withLock {
        val services = discovery.discover().filter { trustStore.extensionId(it) == extensionId }
        services.forEach { service -> trustStore.setEnabled(service, false) }
        val removed = deactivateExtension(extensionId)
        removed || services.isNotEmpty()
    }

    override suspend fun revoke(packageName: String, serviceName: String) {
        lifecycleMutex.withLock {
            val trustedService = trustStore.trustedServices().firstOrNull { record ->
                record.packageName == packageName && record.serviceName == serviceName
            }
            deactivateService(ExtensionServiceKey(packageName, serviceName))
            trustedService?.extensionId?.takeIf { extensionId ->
                trustStore.isSoleStoredOwner(packageName, serviceName, extensionId)
            }?.let { extensionId ->
                clearExtensionData(extensionId)
            }
            trustStore.revoke(packageName, serviceName)
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
        clearExtensionData(extensionId)
    }

    private suspend fun clearExtensionData(extensionId: String): PluginDataClearResult.Cleared {
        val id = ExtensionId(extensionId)
        val snapshot = extensionSettingStore.snapshot(extensionId)
        extensionSettingsRepository.clear(id)
        return PluginDataClearResult.Cleared(
            clearedSettingValues = snapshot.values.size,
            clearedCredentialHandles = snapshot.credentialHandles.size,
            clearedEpgSources = subscriptionProviderImporter.clearExtensionEpg(id),
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
            )
        )
    }

    override suspend fun restoreEnabled(): Int {
        if (!settings[PreferencesKeys.EXTERNAL_EXTENSIONS]) return 0
        var restored = 0
        val services = discovery.discover()
        pruneMissingServices(services)
        services
            .filter { service -> trustStore.isTrusted(service) && trustStore.isEnabled(service) }
            .forEach { service ->
                if (hasAvailableRegistration(service)) {
                    restored++
                } else {
                    val result = lifecycleMutex.withLock {
                        val registered = registeredExtension(service)
                        if (registered != null && hasAvailableRegistration(service)) {
                            PluginEnableResult.Enabled(registered.manifest)
                        } else {
                            enableServiceLocked(service, reauthorizeCapabilities = false)
                        }
                    }
                    if (result is PluginEnableResult.Enabled) restored++
                }
            }
        return restored
    }

    private fun suspendExternalTransports() {
        transports.removeAll().forEach(::deactivateTransport)
    }

    private suspend fun pruneMissingServices(discovered: List<InstalledExtensionService>) =
        lifecycleMutex.withLock {
            val installedKeys = discovered.mapTo(mutableSetOf(), InstalledExtensionService::key)
            transports.removeMissing(installedKeys).forEach(::deactivateTransport)
        }

    private suspend fun ensureConnected(service: InstalledExtensionService) {
        if (hasAvailableRegistration(service)) return
        lifecycleMutex.withLock {
            if (!hasAvailableRegistration(service)) {
                enableServiceLocked(service, reauthorizeCapabilities = false)
            }
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

    private fun deactivateService(key: ExtensionServiceKey): Boolean {
        val active = transports.remove(key) ?: return false
        deactivateTransport(active)
        return true
    }

    private fun deactivateTransport(
        active: ActiveExtensionTransport<ExtensionPluginTransport>,
    ) {
        runtime.unregister(ExtensionId(active.extensionId))
        active.transport.close()
    }

    private fun deactivateExtension(extensionId: String): Boolean {
        val removed = transports.removeByExtensionId(extensionId)
        removed.forEach { active -> active.transport.close() }
        if (removed.isNotEmpty()) runtime.unregister(ExtensionId(extensionId))
        return removed.isNotEmpty()
    }

    private suspend fun isAutomaticConnectionAllowed(service: InstalledExtensionService): Boolean =
        settings[PreferencesKeys.EXTERNAL_EXTENSIONS] &&
            trustStore.isTrusted(service) &&
            trustStore.isEnabled(service)

    private fun quarantine(
        service: InstalledExtensionService,
        reason: String,
    ): PluginEnableResult.Rejected {
        if (trustStore.hasPinnedCertificate(service)) {
            trustStore.setEnabled(service, false)
        }
        deactivateService(service.key)
        return PluginEnableResult.Rejected(reason)
    }

    private suspend fun inspect(service: InstalledExtensionService): Result<ExtensionManifest> {
        service.incompatibilityReason?.let { reason ->
            return Result.failure(IllegalArgumentException(reason))
        }
        val transport = runCatching {
            withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
                transportConnector.connect(service)
            }
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        } ?: return Result.failure(IllegalStateException("Extension connection timed out"))
        return runCatching {
            try {
                runtime.validateExternalManifest(transport.manifest)?.let { error ->
                    throw IllegalArgumentException(error.message)
                }
                transport.manifest
            } finally {
                transport.close()
            }
        }.onFailure { error ->
            if (error is CancellationException) throw error
        }
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
            settings: Settings,
        ): ExtensionPluginRepositoryImpl = ExtensionPluginRepositoryImpl(
            discovery = discovery,
            trustStore = trustStore,
            transportConnector = transportConnector,
            runtime = runtime,
            extensionSettingsRepository = extensionSettingsRepository,
            extensionSettingStore = extensionSettingStore,
            subscriptionProviderImporter = subscriptionProviderImporter,
            settings = settings,
            observeSettingsChanges = false,
        )

        private const val CONNECT_TIMEOUT_MILLIS = 5_000L
        private val DIAGNOSTICS_JSON = Json {
            prettyPrint = true
            explicitNulls = false
        }
    }
}

@Serializable
private data class PluginDiagnostics(
    val formatVersion: Int = 1,
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
    val declaredHooks: List<String>,
    val consecutiveFailures: Int,
    val storedValueCount: Int,
    val storedCredentialHandleCount: Int,
    val inspectionAvailable: Boolean,
)
