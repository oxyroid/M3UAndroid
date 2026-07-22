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
    data class Registered(val extension: RegisteredExtension) : ExtensionRegistrationResult
    data class Rejected(val error: ExtensionError) : ExtensionRegistrationResult
}

class ExtensionRuntime(
    private val hostApiVersion: ExtensionApiVersion,
    private val invocationIdFactory: InvocationIdFactory = UuidInvocationIdFactory(),
    private val capabilityPolicy: CapabilityPolicy = DeclaredCapabilityPolicy,
    private val settingsProvider: ExtensionSettingsProvider = EmptyExtensionSettingsProvider,
    private val invocationPolicy: InvocationPolicy = InvocationPolicy(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
) : ExtensionCatalog {
    private val registrations = ConcurrentHashMap<ExtensionId, Registration>()

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
        if (registrations.putIfAbsent(manifest.id, registration) != null) {
            return ExtensionRegistrationResult.Rejected(
                ExtensionError(
                    code = ExtensionErrorCodes.ExtensionAlreadyRegistered,
                    message = "Extension ${manifest.id} is already registered",
                    recoverable = false,
                )
            )
        }
        return ExtensionRegistrationResult.Registered(registration.publicModel())
    }

    fun register(transport: ExtensionTransport): ExtensionRegistrationResult {
        val manifest = transport.manifest
        validateExternalManifest(manifest)?.let { error ->
            return ExtensionRegistrationResult.Rejected(error)
        }
        val registration = Registration(
            manifest = manifest,
            handlers = emptyMap(),
            transport = transport,
            semaphore = Semaphore(invocationPolicy.maxConcurrentInvocationsPerExtension),
            unhealthyFailureThreshold = invocationPolicy.unhealthyFailureThreshold,
        )
        if (registrations.putIfAbsent(manifest.id, registration) != null) {
            return ExtensionRegistrationResult.Rejected(
                ExtensionError(
                    ExtensionErrorCodes.ExtensionAlreadyRegistered,
                    "Extension ${manifest.id} is already registered",
                    false,
                )
            )
        }
        return ExtensionRegistrationResult.Registered(registration.publicModel())
    }

    fun unregister(extensionId: ExtensionId): RegisteredExtension? =
        registrations.remove(extensionId)?.publicModel()

    fun setEnabled(extensionId: ExtensionId, enabled: Boolean): RegisteredExtension? {
        val registration = registrations[extensionId] ?: return null
        registration.enabled = enabled
        if (enabled) registration.failures.set(0)
        return registration.publicModel()
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
    ): ExtensionResult<Response> {
        val invocationId = invocationIdFactory.create()
        val registration = registrations[extensionId]
            ?: return failure(invocationId, extensionId, spec, ExtensionErrorCodes.ExtensionNotFound, "Extension $extensionId is not registered", true)
        if (!registration.enabled) {
            return failure(invocationId, extensionId, spec, ExtensionErrorCodes.ExtensionDisabled, "Extension $extensionId is disabled", true)
        }
        if (registration.failures.get() >= invocationPolicy.unhealthyFailureThreshold) {
            return failure(invocationId, extensionId, spec, ExtensionErrorCodes.ExtensionUnhealthy, "Extension $extensionId is unhealthy", true)
        }
        val declaration = registration.manifest.hooks.singleOrNull { candidate -> candidate.hook == spec.hook }
            ?: return failure(invocationId, extensionId, spec, ExtensionErrorCodes.HookNotDeclared, "Extension $extensionId does not declare ${spec.hook}", false)
        if (declaration.schemaVersion != spec.schemaVersion) {
            return failure(invocationId, extensionId, spec, ExtensionErrorCodes.SchemaIncompatible, "Hook schema version is incompatible", false)
        }
        val granted = capabilityPolicy.grants(registration.manifest, spec.hook)
            .intersect(registration.manifest.capabilities.mapTo(mutableSetOf()) { it.capability })
        val missing = declaration.requiredCapabilities - granted
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
                    registration.invoke(
                        spec = spec,
                        context = context,
                        request = request,
                        brokerScope = brokerScope,
                        json = json,
                        hostApiVersion = hostApiVersion,
                    )
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
                    rawOutcome
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
                } else {
                    HookResult.Failure(sanitize(rawOutcome.error))
                }
            }
        }
        if (invocation.runtimeFailure) {
            registration.failures.incrementAndGet()
        } else {
            registration.failures.set(0)
        }
        return ExtensionResult(invocationId, extensionId, spec, outcome)
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
            manifest.displayName.length > MAX_MANIFEST_DISPLAY_NAME_LENGTH ||
                (manifest.extensionVersion.preRelease?.length ?: 0) >
                    MAX_MANIFEST_VERSION_LABEL_LENGTH ||
                manifest.hooks.size > MAX_MANIFEST_HOOKS ||
                manifest.capabilities.size > MAX_MANIFEST_CAPABILITIES ||
                manifest.capabilities.any { request ->
                    request.reason.length > MAX_CAPABILITY_REASON_LENGTH
                } ||
                manifest.metadata.size > MAX_MANIFEST_METADATA_ENTRIES ||
                manifest.metadata.any { (key, value) ->
                    key.isBlank() ||
                        key.length > MAX_MANIFEST_METADATA_KEY_LENGTH ||
                        value.length > MAX_MANIFEST_METADATA_VALUE_LENGTH
                } ||
                (schema != null && (
                    schema.fields.size > MAX_MANIFEST_SETTING_FIELDS ||
                        schema.fields.any { field ->
                            field.label.length > MAX_SETTING_LABEL_LENGTH ||
                                (field.description?.length ?: 0) > MAX_SETTING_DESCRIPTION_LENGTH ||
                                field.choices.size > MAX_SETTING_CHOICES ||
                                field.choices.any { choice ->
                                    choice.label.isBlank() ||
                                        choice.label.length > MAX_SETTING_LABEL_LENGTH ||
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
        val failures: AtomicInteger = AtomicInteger(0),
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
                failures.get() >= unhealthyFailureThreshold -> ExtensionState.UNHEALTHY
                else -> ExtensionState.ENABLED
            },
            consecutiveFailures = failures.get(),
        )
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
            ExtensionErrorCodes.InvocationTimedOut,
            ExtensionErrorCodes.PayloadTooLarge,
            ExtensionErrorCodes.SchemaIncompatible,
        )
        val SENSITIVE_KEY_WORDS = setOf("token", "password", "authorization", "secret", "credential")
        val SENSITIVE_VALUE_PATTERN = Regex(
            "(?i)\\b(token|password|authorization|secret|credential)\\s*[=:]\\s*[^\\s,;]+",
        )
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
