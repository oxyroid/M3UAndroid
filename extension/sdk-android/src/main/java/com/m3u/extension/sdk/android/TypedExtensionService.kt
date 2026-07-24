package com.m3u.extension.sdk.android

import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.Hook
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import com.m3u.extension.api.security.HostNetworkBrokerHooks
import com.m3u.extension.runtime.ExtensionTransport
import com.m3u.extension.runtime.ExtensionTransportHealth
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * APK-side service that maps typed [HookSpec] handlers to the Android extension transport.
 * Request and response serialization, error envelopes, cancellation, and health are handled here.
 */
abstract class TypedExtensionService : ExtensionService() {
    protected abstract val extensionManifest: ExtensionManifest

    private val registry = TypedHookRegistry()
    private val transportDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        registry.createTransport(extensionManifest, json)
    }

    final override val transport: ExtensionTransport
        get() = transportDelegate.value

    final override suspend fun invoke(
        envelope: SerializedExtensionEnvelope,
        hostNetworkBroker: ExtensionHostNetworkBroker,
    ): SerializedExtensionResult = transportDelegate.value.invoke(envelope, hostNetworkBroker)

    /** Register a handler that returns a successful typed response. */
    protected fun <Request : ExtensionPayload, Response : ExtensionPayload> handle(
        spec: HookSpec<Request, Response>,
        handler: suspend (request: Request, context: ExtensionCallContext) -> Response,
    ) {
        check(!transportDelegate.isInitialized()) {
            "Hooks must be registered before the extension service is bound"
        }
        registry.handle(spec) { request, context ->
            HookResult.Success(handler(request, context))
        }
    }

    /** Register a handler that can return a contract error without throwing. */
    protected fun <Request : ExtensionPayload, Response : ExtensionPayload> handleResult(
        spec: HookSpec<Request, Response>,
        handler: suspend (request: Request, context: ExtensionCallContext) -> HookResult<Response>,
    ) {
        check(!transportDelegate.isInitialized()) {
            "Hooks must be registered before the extension service is bound"
        }
        registry.handle(spec, handler)
    }

    /** Register a supported Hook handler that uses a short-lived host network broker scope. */
    protected fun <Request : ExtensionPayload, Response : ExtensionPayload> handleWithBroker(
        spec: HookSpec<Request, Response>,
        handler: suspend (
            request: Request,
            context: ExtensionCallContext,
            broker: ExtensionHostNetworkBroker,
        ) -> Response,
    ) {
        check(!transportDelegate.isInitialized()) {
            "Hooks must be registered before the extension service is bound"
        }
        registry.handleWithBroker(spec) { request, context, broker ->
            HookResult.Success(handler(request, context, broker))
        }
    }

    /** Register a supported broker-backed handler that can return a contract error. */
    protected fun <Request : ExtensionPayload, Response : ExtensionPayload> handleResultWithBroker(
        spec: HookSpec<Request, Response>,
        handler: suspend (
            request: Request,
            context: ExtensionCallContext,
            broker: ExtensionHostNetworkBroker,
        ) -> HookResult<Response>,
    ) {
        check(!transportDelegate.isInitialized()) {
            "Hooks must be registered before the extension service is bound"
        }
        registry.handleWithBroker(spec, handler)
    }
}

internal class TypedHookRegistry {
    private val bindings = linkedMapOf<Hook, TypedHookBinding>()
    private var sealed = false

    fun <Request : ExtensionPayload, Response : ExtensionPayload> handle(
        spec: HookSpec<Request, Response>,
        handler: suspend (request: Request, context: ExtensionCallContext) -> HookResult<Response>,
    ) {
        register(spec, requiresBroker = false) { request, context, _ ->
            handler(request, context)
        }
    }

    fun <Request : ExtensionPayload, Response : ExtensionPayload> handleWithBroker(
        spec: HookSpec<Request, Response>,
        handler: suspend (
            request: Request,
            context: ExtensionCallContext,
            broker: ExtensionHostNetworkBroker,
        ) -> HookResult<Response>,
    ) {
        register(spec, requiresBroker = true) { request, context, broker ->
            handler(request, context, checkNotNull(broker))
        }
    }

    private fun <Request : ExtensionPayload, Response : ExtensionPayload> register(
        spec: HookSpec<Request, Response>,
        requiresBroker: Boolean,
        handler: suspend (
            request: Request,
            context: ExtensionCallContext,
            broker: ExtensionHostNetworkBroker?,
        ) -> HookResult<Response>,
    ) {
        check(!sealed) { "Hooks cannot be registered after the transport is created" }
        check(spec.hook !in bindings) { "Hook ${spec.hook} is already registered" }
        require(!requiresBroker || HostNetworkBrokerHooks.supports(spec.hook)) {
            "Host network broker is not available to Hook ${spec.hook.id}"
        }
        bindings[spec.hook] = TypedHookBindingImpl(spec, requiresBroker, handler)
    }

    fun createTransport(
        manifest: ExtensionManifest,
        json: Json,
    ): BrokerAwareExtensionTransport {
        sealed = true
        return TypedExtensionTransport(manifest, bindings.values.toList(), json)
    }
}

private interface TypedHookBinding {
    val spec: HookSpec<*, *>

    suspend fun invoke(
        payload: JsonElement,
        context: ExtensionCallContext,
        hostNetworkBroker: ExtensionHostNetworkBroker?,
        json: Json,
    ): TypedHookOutcome
}

private class TypedHookBindingImpl<Request : ExtensionPayload, Response : ExtensionPayload>(
    override val spec: HookSpec<Request, Response>,
    private val requiresBroker: Boolean,
    private val handler: suspend (
        request: Request,
        context: ExtensionCallContext,
        broker: ExtensionHostNetworkBroker?,
    ) -> HookResult<Response>,
) : TypedHookBinding {
    override suspend fun invoke(
        payload: JsonElement,
        context: ExtensionCallContext,
        hostNetworkBroker: ExtensionHostNetworkBroker?,
        json: Json,
    ): TypedHookOutcome {
        if (requiresBroker && hostNetworkBroker == null) {
            return TypedHookOutcome.Failure(
                ExtensionError(
                    code = ExtensionErrorCodes.InvocationFailed,
                    message = "Host network broker is unavailable",
                    recoverable = true,
                )
            )
        }
        val request = runCatching {
            json.decodeFromJsonElement(spec.requestSerializer, payload)
        }.getOrElse {
            return TypedHookOutcome.Failure(
                ExtensionError(
                    code = ExtensionErrorCodes.SchemaIncompatible,
                    message = "Extension request payload is invalid",
                    recoverable = false,
                )
            )
        }
        return when (val result = handler(request, context, hostNetworkBroker)) {
            is HookResult.Success -> runCatching {
                TypedHookOutcome.Success(
                    json.encodeToJsonElement(spec.responseSerializer, result.payload)
                )
            }.getOrElse {
                TypedHookOutcome.Failure(
                    ExtensionError(
                        code = ExtensionErrorCodes.SchemaIncompatible,
                        message = "Extension response payload is invalid",
                        recoverable = false,
                    )
                )
            }
            is HookResult.Failure -> TypedHookOutcome.Failure(result.error)
        }
    }
}

private sealed interface TypedHookOutcome {
    data class Success(val payload: JsonElement) : TypedHookOutcome
    data class Failure(val error: ExtensionError) : TypedHookOutcome
}

internal interface BrokerAwareExtensionTransport : ExtensionTransport {
    suspend fun invoke(
        request: SerializedExtensionEnvelope,
        hostNetworkBroker: ExtensionHostNetworkBroker?,
    ): SerializedExtensionResult
}

private class TypedExtensionTransport(
    override val manifest: ExtensionManifest,
    bindings: Collection<TypedHookBinding>,
    private val json: Json,
) : BrokerAwareExtensionTransport {
    private val bindings = bindings.associateBy { binding -> binding.spec.hook }
    private val invocations = InvocationRegistry()

    init {
        val declarations = manifest.hooks.associateBy { declaration -> declaration.hook }
        val missingBindings = declarations.keys - this.bindings.keys
        val undeclaredBindings = this.bindings.keys - declarations.keys
        val schemaMismatches = this.bindings.values
            .filter { binding ->
                declarations[binding.spec.hook]?.schemaVersion != binding.spec.schemaVersion
            }
            .mapTo(mutableSetOf()) { binding -> binding.spec.hook }
        require(missingBindings.isEmpty() && undeclaredBindings.isEmpty() && schemaMismatches.isEmpty()) {
            buildString {
                append("Typed Hook registrations do not match the extension manifest")
                if (missingBindings.isNotEmpty()) append("; missing=$missingBindings")
                if (undeclaredBindings.isNotEmpty()) append("; undeclared=$undeclaredBindings")
                if (schemaMismatches.isNotEmpty()) append("; schemaMismatch=$schemaMismatches")
            }
        }
    }

    override suspend fun invoke(request: SerializedExtensionEnvelope): SerializedExtensionResult =
        invoke(request, hostNetworkBroker = null)

    override suspend fun invoke(
        request: SerializedExtensionEnvelope,
        hostNetworkBroker: ExtensionHostNetworkBroker?,
    ): SerializedExtensionResult {
        validate(request)?.let { error -> return request.errorResult(error) }
        val binding = bindings.getValue(request.hook)
        val declaredCapabilities = manifest.capabilities
            .mapTo(mutableSetOf()) { capabilityRequest -> capabilityRequest.capability }
        val grantedCapabilities = request.grantedCapabilities.intersect(declaredCapabilities).toSet()
        val declaration = manifest.hooks.single { candidate -> candidate.hook == request.hook }
        val missingCapabilities = declaration.requiredCapabilities - grantedCapabilities
        if (missingCapabilities.isNotEmpty()) {
            return request.errorResult(
                ExtensionError(
                    code = ExtensionErrorCodes.CapabilityDenied,
                    message = "Invocation is missing required capabilities",
                    recoverable = true,
                    details = mapOf(
                        "missingCapabilities" to missingCapabilities.joinToString { capability ->
                            capability.id
                        }
                    ),
                )
            )
        }
        val context = ExtensionCallContext(
            invocationId = request.invocationId,
            extensionId = manifest.id,
            grantedCapabilities = grantedCapabilities,
            settings = request.settings,
        )
        val job = currentCoroutineContext()[Job]
        if (job != null) {
            when (invocations.register(request.invocationId, job)) {
                InvocationRegistration.REGISTERED -> Unit
                InvocationRegistration.CANCELLED -> throw CancellationException(
                    "Extension invocation was cancelled by the host"
                )
                InvocationRegistration.DUPLICATE -> return request.errorResult(
                    ExtensionError(
                        code = ExtensionErrorCodes.InvocationFailed,
                        message = "Invocation id was already used",
                        recoverable = false,
                    )
                )
            }
        }
        return try {
            when (
                val outcome = binding.invoke(
                    payload = request.payload,
                    context = context,
                    hostNetworkBroker = hostNetworkBroker,
                    json = json,
                )
            ) {
                is TypedHookOutcome.Success -> request.successResult(outcome.payload)
                is TypedHookOutcome.Failure -> request.errorResult(outcome.error)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            request.errorResult(
                ExtensionError(
                    code = ExtensionErrorCodes.InvocationFailed,
                    message = "Extension handler failed",
                    recoverable = true,
                    details = mapOf("exception" to exception.javaClass.simpleName),
                )
            )
        } finally {
            if (job != null) invocations.complete(request.invocationId, job)
        }
    }

    override suspend fun cancel(invocationId: InvocationId) {
        invocations.cancel(invocationId)
    }

    override suspend fun health(): ExtensionTransportHealth = ExtensionTransportHealth.HEALTHY

    private fun validate(request: SerializedExtensionEnvelope): ExtensionError? {
        if (request.extensionId != manifest.id) {
            return ExtensionError(
                code = ExtensionErrorCodes.RegistrationInvalid,
                message = "Invocation extension id does not match this service",
                recoverable = false,
            )
        }
        if (
            manifest.apiRange.minimum.major != request.apiVersion.major ||
            manifest.apiRange.maximum.major != request.apiVersion.major
        ) {
            return ExtensionError(
                code = ExtensionErrorCodes.ApiIncompatible,
                message = "Invocation API version is incompatible",
                recoverable = false,
            )
        }
        val binding = bindings[request.hook]
            ?: return ExtensionError(
                code = ExtensionErrorCodes.HookNotDeclared,
                message = "Extension does not implement ${request.hook}",
                recoverable = false,
            )
        if (binding.spec.schemaVersion != request.schemaVersion) {
            return ExtensionError(
                code = ExtensionErrorCodes.SchemaIncompatible,
                message = "Hook schema version is incompatible",
                recoverable = false,
            )
        }
        return null
    }

    private fun SerializedExtensionEnvelope.successResult(
        responsePayload: JsonElement,
    ): SerializedExtensionResult = SerializedExtensionResult(
        invocationId = invocationId,
        extensionId = manifest.id,
        hook = hook,
        schemaVersion = schemaVersion,
        payload = responsePayload,
    )

    private fun SerializedExtensionEnvelope.errorResult(
        extensionError: ExtensionError,
    ): SerializedExtensionResult = SerializedExtensionResult(
        invocationId = invocationId,
        extensionId = manifest.id,
        hook = hook,
        schemaVersion = schemaVersion,
        error = extensionError,
    )
}

private enum class InvocationRegistration {
    REGISTERED,
    CANCELLED,
    DUPLICATE,
}

private class InvocationRegistry(
    private val maximumRememberedIds: Int = 1_024,
) {
    private val active = mutableMapOf<InvocationId, Job>()
    private val cancelledBeforeRegistration = linkedSetOf<InvocationId>()
    private val completed = linkedSetOf<InvocationId>()

    init {
        require(maximumRememberedIds > 0) { "Remembered invocation limit must be positive" }
    }

    fun register(invocationId: InvocationId, job: Job): InvocationRegistration = synchronized(this) {
        when {
            cancelledBeforeRegistration.remove(invocationId) -> {
                completed.remember(invocationId)
                InvocationRegistration.CANCELLED
            }
            invocationId in active || invocationId in completed -> InvocationRegistration.DUPLICATE
            else -> {
                active[invocationId] = job
                InvocationRegistration.REGISTERED
            }
        }
    }

    fun complete(invocationId: InvocationId, job: Job) = synchronized(this) {
        if (active.remove(invocationId, job)) completed.remember(invocationId)
    }

    fun cancel(invocationId: InvocationId) {
        val job = synchronized(this) {
            active[invocationId] ?: run {
                if (invocationId !in completed) {
                    cancelledBeforeRegistration.remember(invocationId)
                }
                null
            }
        }
        job?.cancel(CancellationException("Extension invocation was cancelled by the host"))
    }

    private fun LinkedHashSet<InvocationId>.remember(invocationId: InvocationId) {
        remove(invocationId)
        add(invocationId)
        while (size > maximumRememberedIds) {
            val iterator = iterator()
            iterator.next()
            iterator.remove()
        }
    }
}
