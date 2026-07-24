package com.m3u.extension.runtime

import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionContractCatalog
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionHandler
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.Hook
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.ExtensionResult
import com.m3u.extension.api.security.BrokerScopeHandle
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.encodeToStream

fun interface InvocationIdFactory {
    fun create(): InvocationId
}

class UuidInvocationIdFactory : InvocationIdFactory {
    override fun create(): InvocationId = InvocationId(UUID.randomUUID().toString())
}

data class InvocationPolicy(
    val timeoutMillis: Long = 30_000,
    val maxConcurrentInvocationsPerExtension: Int = 4,
    val maxPayloadBytes: Int = 1_048_576,
    val unhealthyFailureThreshold: Int = 3,
) {
    init {
        require(timeoutMillis > 0) { "Invocation timeout must be positive" }
        require(maxConcurrentInvocationsPerExtension > 0) { "Invocation concurrency must be positive" }
        require(maxPayloadBytes > 0) { "Payload limit must be positive" }
        require(unhealthyFailureThreshold > 0) { "Failure threshold must be positive" }
    }
}

fun interface CapabilityPolicy {
    fun grants(manifest: ExtensionManifest, hook: Hook): Set<Capability>
}

object DeclaredCapabilityPolicy : CapabilityPolicy {
    override fun grants(manifest: ExtensionManifest, hook: Hook): Set<Capability> =
        manifest.capabilities.mapTo(mutableSetOf()) { request -> request.capability }
}

fun interface ExtensionSettingsProvider {
    fun snapshot(manifest: ExtensionManifest): ExtensionSettingsSnapshot
}

object EmptyExtensionSettingsProvider : ExtensionSettingsProvider {
    override fun snapshot(manifest: ExtensionManifest): ExtensionSettingsSnapshot =
        ExtensionSettingsSnapshot()
}

interface ExtensionCatalog {
    fun registeredExtensions(): List<RegisteredExtension>
    fun extensionsSupporting(hook: Hook): List<RegisteredExtension>
}

data class RegisteredExtension(
    val manifest: ExtensionManifest,
    val boundHooks: Set<Hook>,
    val executionKind: ExtensionExecutionKind,
    val state: ExtensionState,
    val consecutiveFailures: Int,
)

enum class ExtensionExecutionKind {
    BUILT_IN,
    EXTERNAL,
}

sealed interface ExtensionRegistrationResult {
    data class Registered(
        val extension: RegisteredExtension,
        val registrationToken: ExtensionRegistrationToken? = null,
    ) : ExtensionRegistrationResult
    data class Rejected(val error: ExtensionError) : ExtensionRegistrationResult
}

class ExtensionRegistrationToken private constructor() {
    override fun toString(): String = "ExtensionRegistrationToken(opaque)"

    companion object {
        internal fun create(): ExtensionRegistrationToken = ExtensionRegistrationToken()
    }
}

class ExtensionRuntime(
    private val hostApiVersion: ExtensionApiVersion,
    private val invocationIdFactory: InvocationIdFactory = UuidInvocationIdFactory(),
    private val capabilityPolicy: CapabilityPolicy = DeclaredCapabilityPolicy,
    private val settingsProvider: ExtensionSettingsProvider = EmptyExtensionSettingsProvider,
    private val brokerScopeProvider: ExtensionBrokerScopeProvider =
        EmptyExtensionBrokerScopeProvider,
    private val invocationPolicy: InvocationPolicy = InvocationPolicy(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : ExtensionCatalog {
    private val registrations = ConcurrentHashMap<ExtensionId, Registration>()
    private val externalFailureTrackers =
        ConcurrentHashMap<ExtensionId, ExternalFailureTracker>()
    private val registrationLifecycleLock = Any()

    fun register(entrypoint: ExtensionEntrypoint): ExtensionRegistrationResult {
        val manifest = entrypoint.manifest
        if (!isApiMajorCompatible(manifest)) {
            return ExtensionRegistrationResult.Rejected(incompatibleApiError(manifest))
        }
        validateManifestBounds(manifest)?.let { error ->
            return ExtensionRegistrationResult.Rejected(error)
        }
        val handlers = entrypoint.handlers.toList()
        val duplicateHooks = handlers.groupingBy { handler -> handler.spec.hook }
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        if (duplicateHooks.isNotEmpty()) {
            return invalidRegistration(manifest, "Extension binds a hook more than once", duplicateHooks)
        }
        val declarations = manifest.hooks.associateBy { declaration -> declaration.hook }
        val boundHooks = handlers.mapTo(mutableSetOf()) { handler -> handler.spec.hook }
        val missingHooks = declarations.keys - boundHooks
        val undeclaredHooks = boundHooks - declarations.keys
        val incompatibleSchemas = handlers.filter { handler ->
            declarations[handler.spec.hook]?.schemaVersion != handler.spec.schemaVersion
        }.mapTo(mutableSetOf()) { handler -> handler.spec.hook }
        if (missingHooks.isNotEmpty() || undeclaredHooks.isNotEmpty() || incompatibleSchemas.isNotEmpty()) {
            return ExtensionRegistrationResult.Rejected(
                ExtensionError(
                    code = ExtensionErrorCodes.RegistrationInvalid,
                    message = "Extension hook bindings do not match its manifest",
                    recoverable = false,
                    details = buildMap {
                        if (missingHooks.isNotEmpty()) put("missingHooks", missingHooks.joinToString(transform = Hook::id))
                        if (undeclaredHooks.isNotEmpty()) put("undeclaredHooks", undeclaredHooks.joinToString(transform = Hook::id))
                        if (incompatibleSchemas.isNotEmpty()) put("schemaMismatch", incompatibleSchemas.joinToString(transform = Hook::id))
                    },
                )
            )
        }
        val registration = Registration(
            manifest = manifest,
            handlers = handlers.associateBy { handler -> handler.spec.hook },
            transport = null,
            semaphore = Semaphore(invocationPolicy.maxConcurrentInvocationsPerExtension),
            unhealthyFailureThreshold = invocationPolicy.unhealthyFailureThreshold,
        )
        synchronized(registrationLifecycleLock) {
            if (registrations.containsKey(manifest.id)) {
                return ExtensionRegistrationResult.Rejected(
                    ExtensionError(
                        code = ExtensionErrorCodes.ExtensionAlreadyRegistered,
                        message = "Extension ${manifest.id} is already registered",
                        recoverable = false,
                    )
                )
            }
            registrations[manifest.id] = registration
        }
        return ExtensionRegistrationResult.Registered(registration.publicModel())
    }

    fun register(transport: ExtensionTransport): ExtensionRegistrationResult {
        val manifest = transport.manifest
        validateExternalManifest(manifest)?.let { error ->
            return ExtensionRegistrationResult.Rejected(error)
        }
        val registrationToken = ExtensionRegistrationToken.create()
        val registration = synchronized(registrationLifecycleLock) {
            if (registrations.containsKey(manifest.id)) {
                return ExtensionRegistrationResult.Rejected(
                    ExtensionError(
                        ExtensionErrorCodes.ExtensionAlreadyRegistered,
                        "Extension ${manifest.id} is already registered",
                        false,
                    )
                )
            }
            val failureTracker = externalFailureTrackers.computeIfAbsent(manifest.id) {
                ExternalFailureTracker()
            }
            val candidate = Registration(
                manifest = manifest,
                handlers = emptyMap(),
                transport = transport,
                semaphore = Semaphore(invocationPolicy.maxConcurrentInvocationsPerExtension),
                unhealthyFailureThreshold = invocationPolicy.unhealthyFailureThreshold,
                registrationToken = registrationToken,
                externalFailureTracker = failureTracker,
                transportHealth = AtomicReference(ExtensionTransportHealth.UNAVAILABLE),
            )
            failureTracker.activate(registrationToken)
            registrations[manifest.id] = candidate
            candidate
        }
        return ExtensionRegistrationResult.Registered(
            extension = registration.publicModel(),
            registrationToken = registrationToken,
        )
    }

    fun unregister(extensionId: ExtensionId): RegisteredExtension? =
        synchronized(registrationLifecycleLock) {
            registrations.remove(extensionId)?.let { registration ->
                registration.deactivate()
                registration.publicModel()
            }
        }

    fun forgetExternalState(extensionId: ExtensionId): Boolean =
        synchronized(registrationLifecycleLock) {
            if (registrations.containsKey(extensionId)) return false
            externalFailureTrackers.remove(extensionId) != null
        }

    fun setEnabled(extensionId: ExtensionId, enabled: Boolean): RegisteredExtension? {
        return synchronized(registrationLifecycleLock) {
            val registration = registrations[extensionId] ?: return null
            registration.enabled = enabled
            if (enabled) {
                registration.resetFailures()
            }
            registration.publicModel()
        }
    }

    fun recordTransportHealth(
        extensionId: ExtensionId,
        registrationToken: ExtensionRegistrationToken,
        health: ExtensionTransportHealth,
    ): RegisteredExtension? {
        return synchronized(registrationLifecycleLock) {
            val registration = registrations[extensionId] ?: return null
            if (!registration.matches(registrationToken)) return null
            registration.transportHealth.set(health)
            registration.publicModel()
        }
    }

    override fun registeredExtensions(): List<RegisteredExtension> = registrations.values
        .map(Registration::publicModel)
        .sortedBy { extension -> extension.manifest.id.value }

    fun validateExternalManifest(manifest: ExtensionManifest): ExtensionError? {
        if (!isApiMajorCompatible(manifest)) return incompatibleApiError(manifest)
        validateManifestBounds(manifest)?.let { return it }
        return externalManifestError(manifest)
    }

    override fun extensionsSupporting(hook: Hook): List<RegisteredExtension> = registrations.values
        .filter { registration -> registration.supports(hook) }
        .map(Registration::publicModel)
        .sortedBy { extension -> extension.manifest.id.value }

    suspend fun <Request : ExtensionPayload, Response : ExtensionPayload> invoke(
        extensionId: ExtensionId,
        spec: HookSpec<Request, Response>,
        request: Request,
        brokerScope: BrokerScopeHandle? = null,
        validateResponse: (Response) -> Unit = {},
    ): ExtensionResult<Response> {
        val invocationId = invocationIdFactory.create()
        val registration = registrations[extensionId]
            ?: return failure(invocationId, extensionId, spec, ExtensionErrorCodes.ExtensionNotFound, "Extension $extensionId is not registered", true)
        if (!registration.enabled) {
            return failure(invocationId, extensionId, spec, ExtensionErrorCodes.ExtensionDisabled, "Extension $extensionId is disabled", true)
        }
        if (registration.isUnhealthy) {
            return failure(invocationId, extensionId, spec, ExtensionErrorCodes.ExtensionUnhealthy, "Extension $extensionId is unhealthy", true)
        }
        val declaration = registration.manifest.hooks.singleOrNull { candidate -> candidate.hook == spec.hook }
            ?: return failure(invocationId, extensionId, spec, ExtensionErrorCodes.HookNotDeclared, "Extension $extensionId does not declare ${spec.hook}", false)
        if (declaration.schemaVersion != spec.schemaVersion) {
            return failure(invocationId, extensionId, spec, ExtensionErrorCodes.SchemaIncompatible, "Hook schema version is incompatible", false)
        }
        val policyGrants = capabilityPolicy.grants(registration.manifest, spec.hook)
            .intersect(registration.manifest.capabilities.mapTo(mutableSetOf()) { it.capability })
        val missing = declaration.requiredCapabilities - policyGrants
        if (missing.isNotEmpty()) {
            return failure(
                invocationId,
                extensionId,
                spec,
                ExtensionErrorCodes.CapabilityDenied,
                "Invocation is missing required capabilities",
                true,
                mapOf("missingCapabilities" to missing.joinToString(transform = Capability::id)),
            )
        }
        val granted = policyGrants.intersect(declaration.requiredCapabilities)
        val settings = runCatching { settingsProvider.snapshot(registration.manifest) }
            .getOrElse {
                return failure(
                    invocationId,
                    extensionId,
                    spec,
                    ExtensionErrorCodes.InvocationFailed,
                    "Extension settings are unavailable",
                    true,
                )
            }
        val payloadSize = json.encodeToString(spec.requestSerializer, request).encodeToByteArray().size +
            json.encodeToString(ExtensionSettingsSnapshot.serializer(), settings).encodeToByteArray().size
        if (payloadSize > invocationPolicy.maxPayloadBytes) {
            return failure(invocationId, extensionId, spec, ExtensionErrorCodes.PayloadTooLarge, "Invocation payload exceeds the host limit", false)
        }
        val context = ExtensionCallContext(invocationId, extensionId, granted, settings)
        val invocation = try {
            withTimeout(invocationPolicy.timeoutMillis) {
                registration.semaphore.withPermit {
                    val managedBrokerScope = if (
                        brokerScope == null &&
                        registration.isExternal
                    ) {
                        try {
                            brokerScopeProvider.open(
                                ExtensionBrokerScopeRequest(
                                    manifest = registration.manifest,
                                    hook = spec.hook,
                                    payload = request,
                                    settings = settings,
                                    grantedCapabilities = granted,
                                )
                            )
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            return@withPermit InvocationAttempt(
                                outcome = HookResult.Failure(
                                    ExtensionError(
                                        ExtensionErrorCodes.InvocationFailed,
                                        "Host broker scope is unavailable",
                                        true,
                                    )
                                ),
                                runtimeFailure = false,
                            )
                        }
                    } else {
                        null
                    }
                    withManagedBrokerScope(managedBrokerScope) {
                        registration.invoke(
                            spec = spec,
                            context = context,
                            request = request,
                            brokerScope = brokerScope ?: managedBrokerScope?.handle,
                            json = json,
                            hostApiVersion = hostApiVersion,
                        )
                    }
                }
            }
        } catch (cancellation: TimeoutCancellationException) {
            InvocationAttempt(
                outcome = HookResult.Failure(
                    ExtensionError(ExtensionErrorCodes.InvocationTimedOut, "Extension invocation timed out", true)
                ),
                runtimeFailure = true,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            InvocationAttempt(
                outcome = HookResult.Failure(
                    ExtensionError(
                        code = ExtensionErrorCodes.InvocationFailed,
                        message = "Extension invocation failed",
                        recoverable = true,
                        details = mapOf("exception" to exception.javaClass.simpleName),
                    )
                ),
                runtimeFailure = true,
            )
        }
        val outcome = when (val rawOutcome = invocation.outcome) {
            is HookResult.Success -> {
                val responseBytes = runCatching {
                    json.encodeToString(spec.responseSerializer, rawOutcome.payload)
                        .encodeToByteArray().size
                }.getOrElse { Int.MAX_VALUE }
                if (responseBytes > invocationPolicy.maxPayloadBytes) {
                    invocation.runtimeFailure = true
                    HookResult.Failure(
                        ExtensionError(
                            ExtensionErrorCodes.PayloadTooLarge,
                            "Extension response exceeds the host limit",
                            false,
                        )
                    )
                } else {
                    try {
                        validateResponse(rawOutcome.payload)
                        rawOutcome
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        invocation.runtimeFailure = true
                        HookResult.Failure(
                            ExtensionError(
                                ExtensionErrorCodes.ResponseInvalid,
                                "Extension response violates the hook contract",
                                false,
                            )
                        )
                    }
                }
            }
            is HookResult.Failure -> {
                if (!errorEnvelopeFitsPayloadLimit(rawOutcome.error)) {
                    invocation.runtimeFailure = true
                    HookResult.Failure(
                        ExtensionError(
                            ExtensionErrorCodes.PayloadTooLarge,
                            "Extension response exceeds the host limit",
                            false,
                        )
                    )
                } else if (!rawOutcome.error.isSafeForHostDisplay()) {
                    invocation.runtimeFailure = true
                    HookResult.Failure(
                        ExtensionError(
                            ExtensionErrorCodes.ResponseInvalid,
                            "Extension returned an invalid error response",
                            false,
                        )
                    )
                } else {
                    HookResult.Failure(sanitize(rawOutcome.error))
                }
            }
        }
        if (invocation.runtimeFailure) {
            registration.recordInvocationOutcome(runtimeFailure = true)
        } else {
            registration.recordInvocationOutcome(runtimeFailure = false)
        }
        return ExtensionResult(invocationId, extensionId, spec, outcome)
    }

    private suspend fun <T> withManagedBrokerScope(
        lease: ExtensionBrokerScopeLease?,
        block: suspend () -> T,
    ): T {
        var invocationFailure: Throwable? = null
        return try {
            block()
        } catch (failure: Throwable) {
            invocationFailure = failure
            throw failure
        } finally {
            try {
                lease?.close()
            } catch (closeFailure: Throwable) {
                if (invocationFailure != null) {
                    invocationFailure.addSuppressed(closeFailure)
                } else {
                    throw closeFailure
                }
            }
        }
    }

    private fun incompatibleApiError(manifest: ExtensionManifest) = ExtensionError(
        code = ExtensionErrorCodes.ApiIncompatible,
        message = "Host API $hostApiVersion is incompatible with extension ${manifest.id}",
        recoverable = false,
        details = mapOf(
            "hostApiVersion" to hostApiVersion.toString(),
            "minimumApiVersion" to manifest.apiRange.minimum.toString(),
            "maximumApiVersion" to manifest.apiRange.maximum.toString(),
        ),
    )

    private fun isApiMajorCompatible(manifest: ExtensionManifest): Boolean =
        manifest.apiRange.minimum.major == hostApiVersion.major &&
            manifest.apiRange.maximum.major == hostApiVersion.major

    private fun externalManifestError(manifest: ExtensionManifest): ExtensionError? {
        val unsupportedHooks = manifest.hooks.filter { declaration ->
            declaration.schemaVersion !in ExtensionContractCatalog.SupportedHookSchemaVersions[declaration.hook].orEmpty()
        }
        if (unsupportedHooks.isNotEmpty()) {
            return ExtensionError(
                code = ExtensionErrorCodes.SchemaIncompatible,
                message = "Extension declares an unsupported hook schema",
                recoverable = false,
                details = mapOf(
                    "hooks" to unsupportedHooks.joinToString { declaration ->
                        "${declaration.hook.id}@${declaration.schemaVersion}"
                    }
                ),
            )
        }
        val unknownRequiredCapabilities = manifest.capabilities
            .filter { request -> request.required && request.capability !in ExtensionContractCatalog.SupportedCapabilities }
        if (unknownRequiredCapabilities.isNotEmpty()) {
            return ExtensionError(
                code = ExtensionErrorCodes.CapabilityDenied,
                message = "Extension requires capabilities unknown to this host",
                recoverable = false,
                details = mapOf(
                    "capabilities" to unknownRequiredCapabilities.joinToString { it.capability.id }
                ),
            )
        }
        return null
    }

    private fun validateManifestBounds(manifest: ExtensionManifest): ExtensionError? {
        val schema = manifest.settingsSchema
        val invalid =
            !manifest.displayName.isSafeDisplayText(MAX_MANIFEST_DISPLAY_NAME_LENGTH) ||
                (manifest.extensionVersion.preRelease?.length ?: 0) >
                    MAX_MANIFEST_VERSION_LABEL_LENGTH ||
                manifest.hooks.size > MAX_MANIFEST_HOOKS ||
                manifest.capabilities.size > MAX_MANIFEST_CAPABILITIES ||
                manifest.capabilities.any { request ->
                    !request.reason.isSafeDisplayText(MAX_CAPABILITY_REASON_LENGTH)
                } ||
                manifest.metadata.size > MAX_MANIFEST_METADATA_ENTRIES ||
                manifest.metadata.any { (key, value) ->
                    !key.isSafeDisplayText(MAX_MANIFEST_METADATA_KEY_LENGTH) ||
                        !value.isSafeDisplayText(MAX_MANIFEST_METADATA_VALUE_LENGTH)
                } ||
                (schema != null && (
                    schema.fields.size > MAX_MANIFEST_SETTING_FIELDS ||
                        schema.fields.any { field ->
                            !field.label.isSafeDisplayText(MAX_SETTING_LABEL_LENGTH) ||
                                (
                                    field.description?.isSafeDisplayText(
                                        MAX_SETTING_DESCRIPTION_LENGTH
                                    ) == false
                                ) ||
                                field.choices.size > MAX_SETTING_CHOICES ||
                                field.choices.any { choice ->
                                    !choice.label.isSafeDisplayText(MAX_SETTING_LABEL_LENGTH) ||
                                        choice.value.length > MAX_SETTING_CHOICE_VALUE_LENGTH
                                } ||
                                (field.defaultValue?.toString()?.encodeToByteArray()?.size ?: 0) >
                                    MAX_SETTING_DEFAULT_BYTES
                        }
                    )) ||
                runCatching {
                    json.encodeToString(ExtensionManifest.serializer(), manifest)
                        .encodeToByteArray().size
                }.getOrDefault(Int.MAX_VALUE) > MAX_MANIFEST_BYTES
        if (!invalid) return null
        return ExtensionError(
            code = ExtensionErrorCodes.RegistrationInvalid,
            message = "Extension manifest exceeds host limits",
            recoverable = false,
        )
    }

    private fun sanitize(error: ExtensionError): ExtensionError = error.copy(
        message = redact(error.message),
        details = error.details
            .filterKeys { key -> SENSITIVE_KEY_WORDS.none { word -> key.contains(word, ignoreCase = true) } }
            .mapValues { (_, value) -> redact(value) },
    )

    private fun ExtensionError.isSafeForHostDisplay(): Boolean =
        message.isSafeDisplayText(MAX_ERROR_MESSAGE_BYTES, measureUtf8Bytes = true) &&
            details.size <= MAX_ERROR_DETAILS &&
            details.all { (key, value) ->
                key.isSafeDisplayText(MAX_ERROR_DETAIL_KEY_BYTES, measureUtf8Bytes = true) &&
                    value.isSafeDisplayText(MAX_ERROR_DETAIL_VALUE_BYTES, measureUtf8Bytes = true)
            }

    private fun String.isSafeDisplayText(
        maximumLength: Int,
        measureUtf8Bytes: Boolean = false,
    ): Boolean =
        isNotBlank() &&
            (if (measureUtf8Bytes) encodeToByteArray().size else length) <= maximumLength &&
            none { character ->
                character.isISOControl() || character.code in BIDI_CONTROL_CODE_POINTS
            }

    @OptIn(ExperimentalSerializationApi::class)
    private fun errorEnvelopeFitsPayloadLimit(error: ExtensionError): Boolean = runCatching {
        json.encodeToStream(
            serializer = ExtensionError.serializer(),
            value = error,
            stream = PayloadLimitOutputStream(invocationPolicy.maxPayloadBytes),
        )
    }.isSuccess

    private fun redact(value: String): String = SENSITIVE_VALUE_PATTERN.replace(value) { match ->
        "${match.groupValues[1]}=<redacted>"
    }

    private fun invalidRegistration(manifest: ExtensionManifest, message: String, hooks: Set<Hook>) =
        ExtensionRegistrationResult.Rejected(
            ExtensionError(
                code = ExtensionErrorCodes.RegistrationInvalid,
                message = message,
                recoverable = false,
                details = mapOf(
                    "extensionId" to manifest.id.value,
                    "hooks" to hooks.joinToString(transform = Hook::id),
                ),
            )
        )

    private fun <Response : ExtensionPayload> failure(
        invocationId: InvocationId,
        extensionId: ExtensionId,
        spec: HookSpec<*, Response>,
        code: com.m3u.extension.api.ExtensionErrorCode,
        message: String,
        recoverable: Boolean,
        details: Map<String, String> = emptyMap(),
    ) = ExtensionResult(
        invocationId = invocationId,
        extensionId = extensionId,
        spec = spec,
        outcome = HookResult.Failure(ExtensionError(code, message, recoverable, details)),
    )

    private class Registration(
        val manifest: ExtensionManifest,
        val handlers: Map<Hook, ExtensionHandler<*, *>>,
        val transport: ExtensionTransport?,
        val semaphore: Semaphore,
        val unhealthyFailureThreshold: Int,
        val registrationToken: ExtensionRegistrationToken? = null,
        val externalFailureTracker: ExternalFailureTracker? = null,
        val localFailures: AtomicInteger = AtomicInteger(0),
        val transportHealth: AtomicReference<ExtensionTransportHealth> =
            AtomicReference(ExtensionTransportHealth.HEALTHY),
        @Volatile var enabled: Boolean = true,
    ) {
        @Suppress("UNCHECKED_CAST")
        suspend fun <Request : ExtensionPayload, Response : ExtensionPayload> invoke(
            spec: HookSpec<Request, Response>,
            context: ExtensionCallContext,
            request: Request,
            brokerScope: BrokerScopeHandle?,
            json: Json,
            hostApiVersion: ExtensionApiVersion,
        ): InvocationAttempt<Response> {
            val handler = handlers[spec.hook] as? ExtensionHandler<Request, Response>
            if (handler != null) {
                return InvocationAttempt(
                    outcome = handler.invoke(context, request),
                    runtimeFailure = false,
                )
            }
            val currentTransport = transport ?: return InvocationAttempt(
                outcome = HookResult.Failure(
                    ExtensionError(ExtensionErrorCodes.HookNotBound, "Extension did not bind ${spec.hook}", false)
                ),
                runtimeFailure = true,
            )
            val envelope = SerializedExtensionEnvelope(
                apiVersion = hostApiVersion,
                invocationId = context.invocationId,
                extensionId = context.extensionId,
                hook = spec.hook,
                schemaVersion = spec.schemaVersion,
                payload = json.encodeToJsonElement(spec.requestSerializer, request),
                settings = context.settings,
                grantedCapabilities = context.grantedCapabilities,
                brokerScope = brokerScope,
            )
            val result = try {
                currentTransport.invoke(envelope)
            } catch (cancellation: CancellationException) {
                currentTransport.cancel(context.invocationId)
                throw cancellation
            }
            if (result.invocationId != context.invocationId || result.extensionId != context.extensionId ||
                result.hook != spec.hook || result.schemaVersion != spec.schemaVersion
            ) {
                return InvocationAttempt(
                    outcome = HookResult.Failure(
                        ExtensionError(ExtensionErrorCodes.SchemaIncompatible, "Extension response envelope is invalid", false)
                    ),
                    runtimeFailure = true,
                )
            }
            result.error?.let { error ->
                return InvocationAttempt(
                    outcome = HookResult.Failure(error),
                    runtimeFailure = error.code in RUNTIME_FAILURE_CODES,
                )
            }
            return runCatching {
                InvocationAttempt(
                    outcome = HookResult.Success(
                        json.decodeFromJsonElement(spec.responseSerializer, checkNotNull(result.payload))
                    ),
                    runtimeFailure = false,
                )
            }.getOrElse {
                InvocationAttempt(
                    outcome = HookResult.Failure(
                        ExtensionError(ExtensionErrorCodes.SchemaIncompatible, "Extension response payload is invalid", false)
                    ),
                    runtimeFailure = true,
                )
            }
        }

        fun supports(hook: Hook): Boolean = hook in handlers ||
            (transport != null && manifest.hooks.any { declaration -> declaration.hook == hook })

        val isExternal: Boolean
            get() = transport != null

        fun matches(token: ExtensionRegistrationToken): Boolean =
            isExternal && registrationToken === token

        fun deactivate() {
            val token = registrationToken
            val tracker = externalFailureTracker
            if (token != null && tracker != null) {
                tracker.deactivate(token)
            }
        }

        fun recordInvocationOutcome(runtimeFailure: Boolean) {
            val token = registrationToken
            val tracker = externalFailureTracker
            if (token != null && tracker != null) {
                tracker.record(token, runtimeFailure)
            } else if (runtimeFailure) {
                localFailures.incrementAndGet()
            } else {
                localFailures.set(0)
            }
        }

        fun resetFailures() {
            val token = registrationToken
            val tracker = externalFailureTracker
            if (token != null && tracker != null) {
                tracker.reset(token)
            } else {
                localFailures.set(0)
            }
        }

        private val consecutiveFailures: Int
            get() = externalFailureTracker?.count() ?: localFailures.get()

        val isUnhealthy: Boolean
            get() = consecutiveFailures >= unhealthyFailureThreshold ||
                (isExternal && transportHealth.get() != ExtensionTransportHealth.HEALTHY)

        fun publicModel() = RegisteredExtension(
            manifest = manifest,
            boundHooks = if (transport == null) handlers.keys else manifest.hooks.mapTo(mutableSetOf()) { it.hook },
            executionKind = if (transport == null) {
                ExtensionExecutionKind.BUILT_IN
            } else {
                ExtensionExecutionKind.EXTERNAL
            },
            state = when {
                !enabled -> ExtensionState.DISABLED
                isUnhealthy -> ExtensionState.UNHEALTHY
                else -> ExtensionState.ENABLED
            },
            consecutiveFailures = consecutiveFailures,
        )
    }

    private class ExternalFailureTracker {
        private var activeToken: ExtensionRegistrationToken? = null
        private var consecutiveFailures: Int = 0

        @Synchronized
        fun activate(token: ExtensionRegistrationToken) {
            activeToken = token
        }

        @Synchronized
        fun deactivate(token: ExtensionRegistrationToken) {
            if (activeToken === token) activeToken = null
        }

        @Synchronized
        fun record(
            token: ExtensionRegistrationToken,
            runtimeFailure: Boolean,
        ) {
            if (activeToken !== token) return
            if (runtimeFailure) {
                consecutiveFailures = incrementSaturatedFailureCount(consecutiveFailures)
            } else {
                consecutiveFailures = 0
            }
        }

        @Synchronized
        fun reset(token: ExtensionRegistrationToken) {
            if (activeToken === token) consecutiveFailures = 0
        }

        @Synchronized
        fun count(): Int = consecutiveFailures
    }

    private data class InvocationAttempt<Response : ExtensionPayload>(
        val outcome: HookResult<Response>,
        var runtimeFailure: Boolean,
    )

    private class PayloadLimitOutputStream(
        private val maxBytes: Int,
    ) : OutputStream() {
        private var bytesWritten: Int = 0

        override fun write(value: Int) {
            reserve(1)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            reserve(length)
        }

        private fun reserve(byteCount: Int) {
            if (byteCount > maxBytes - bytesWritten) {
                throw PayloadLimitExceededException()
            }
            bytesWritten += byteCount
        }
    }

    private class PayloadLimitExceededException : RuntimeException(null, null, false, false)


    private companion object {
        val RUNTIME_FAILURE_CODES = setOf(
            ExtensionErrorCodes.HookNotBound,
            ExtensionErrorCodes.InvocationFailed,
            ExtensionErrorCodes.ResponseInvalid,
            ExtensionErrorCodes.InvocationTimedOut,
            ExtensionErrorCodes.PayloadTooLarge,
            ExtensionErrorCodes.SchemaIncompatible,
        )
        val SENSITIVE_KEY_WORDS = setOf("token", "password", "authorization", "secret", "credential")
        val SENSITIVE_VALUE_PATTERN = Regex(
            "(?i)\\b(token|password|authorization|secret|credential)\\s*[=:]\\s*[^\\s,;]+",
        )
        val BIDI_CONTROL_CODE_POINTS = (
            (0x202A..0x202E) +
                (0x2066..0x2069) +
                listOf(0x200E, 0x200F)
            ).toSet()
        const val MAX_ERROR_MESSAGE_BYTES = 512
        const val MAX_ERROR_DETAILS = 16
        const val MAX_ERROR_DETAIL_KEY_BYTES = 64
        const val MAX_ERROR_DETAIL_VALUE_BYTES = 512
        const val MAX_MANIFEST_BYTES = 256 * 1024
        const val MAX_MANIFEST_DISPLAY_NAME_LENGTH = 160
        const val MAX_MANIFEST_VERSION_LABEL_LENGTH = 64
        const val MAX_MANIFEST_HOOKS = 64
        const val MAX_MANIFEST_CAPABILITIES = 64
        const val MAX_CAPABILITY_REASON_LENGTH = 1_024
        const val MAX_MANIFEST_METADATA_ENTRIES = 32
        const val MAX_MANIFEST_METADATA_KEY_LENGTH = 64
        const val MAX_MANIFEST_METADATA_VALUE_LENGTH = 1_024
        const val MAX_MANIFEST_SETTING_FIELDS = 100
        const val MAX_SETTING_LABEL_LENGTH = 160
        const val MAX_SETTING_DESCRIPTION_LENGTH = 1_024
        const val MAX_SETTING_CHOICES = 64
        const val MAX_SETTING_CHOICE_VALUE_LENGTH = 512
        const val MAX_SETTING_DEFAULT_BYTES = 4_096
    }
}

internal fun incrementSaturatedFailureCount(value: Int): Int =
    if (value == Int.MAX_VALUE) Int.MAX_VALUE else value + 1
