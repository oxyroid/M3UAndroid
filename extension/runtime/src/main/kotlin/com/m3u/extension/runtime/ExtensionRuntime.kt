package com.m3u.extension.runtime

import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionApiVersion
import com.m3u.extension.api.ExtensionEntrypoint
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionHook
import com.m3u.extension.api.ExtensionHookOutcome
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionInvocation
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionResult
import com.m3u.extension.api.Hook
import com.m3u.extension.api.InvocationId
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap

fun interface InvocationIdFactory {
    fun create(): InvocationId
}

class UuidInvocationIdFactory : InvocationIdFactory {
    override fun create(): InvocationId = InvocationId(UUID.randomUUID().toString())
}

data class RegisteredExtension(
    val manifest: ExtensionManifest,
    val boundHooks: Set<Hook>,
)

sealed interface ExtensionRegistrationResult {
    data class Registered(
        val extension: RegisteredExtension,
    ) : ExtensionRegistrationResult

    data class Rejected(
        val error: ExtensionError,
    ) : ExtensionRegistrationResult
}

class ExtensionRuntime(
    private val hostApiVersion: ExtensionApiVersion,
    private val invocationIdFactory: InvocationIdFactory = UuidInvocationIdFactory(),
) {
    private val registrations = ConcurrentHashMap<ExtensionId, Registration>()

    fun register(entrypoint: ExtensionEntrypoint): ExtensionRegistrationResult {
        val manifest = entrypoint.manifest
        if (hostApiVersion !in manifest.apiRange) {
            return ExtensionRegistrationResult.Rejected(
                incompatibleApiError(manifest)
            )
        }

        val hooks = entrypoint.hooks.toList()
        val duplicateHooks = hooks
            .groupingBy(ExtensionHook::hook)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        if (duplicateHooks.isNotEmpty()) {
            return invalidRegistration(
                manifest = manifest,
                message = "Extension binds the same hook more than once",
                hooks = duplicateHooks,
            )
        }

        val declaredHooks = manifest.hooks.mapTo(mutableSetOf()) { declaration -> declaration.hook }
        val boundHooks = hooks.mapTo(mutableSetOf(), ExtensionHook::hook)
        val missingHooks = declaredHooks - boundHooks
        val undeclaredHooks = boundHooks - declaredHooks
        if (missingHooks.isNotEmpty() || undeclaredHooks.isNotEmpty()) {
            return ExtensionRegistrationResult.Rejected(
                ExtensionError(
                    code = ExtensionErrorCodes.RegistrationInvalid,
                    message = "Extension hook bindings do not match its manifest",
                    recoverable = false,
                    details = buildMap {
                        if (missingHooks.isNotEmpty()) {
                            put("missingHooks", missingHooks.joinToString(transform = Hook::id))
                        }
                        if (undeclaredHooks.isNotEmpty()) {
                            put("undeclaredHooks", undeclaredHooks.joinToString(transform = Hook::id))
                        }
                    },
                )
            )
        }

        val registration = Registration(
            manifest = manifest,
            hooks = hooks.associateBy(ExtensionHook::hook),
        )
        val previous = registrations.putIfAbsent(manifest.id, registration)
        if (previous != null) {
            return ExtensionRegistrationResult.Rejected(
                ExtensionError(
                    code = ExtensionErrorCodes.ExtensionAlreadyRegistered,
                    message = "Extension ${manifest.id} is already registered",
                    recoverable = false,
                )
            )
        }

        return ExtensionRegistrationResult.Registered(registration.asPublicModel())
    }

    fun unregister(extensionId: ExtensionId): RegisteredExtension? =
        registrations.remove(extensionId)?.asPublicModel()

    fun registeredExtensions(): List<RegisteredExtension> = registrations.values
        .map(Registration::asPublicModel)
        .sortedBy { extension -> extension.manifest.id.value }

    fun extensionsSupporting(hook: Hook): List<RegisteredExtension> = registrations.values
        .filter { registration -> hook in registration.hooks }
        .map(Registration::asPublicModel)
        .sortedBy { extension -> extension.manifest.id.value }

    suspend fun invoke(
        extensionId: ExtensionId,
        hook: Hook,
        grantedCapabilities: Set<Capability>,
        payload: ExtensionPayload,
    ): ExtensionResult {
        val invocationId = invocationIdFactory.create()
        val registration = registrations[extensionId]
            ?: return failure(
                invocationId = invocationId,
                extensionId = extensionId,
                hook = hook,
                error = ExtensionError(
                    code = ExtensionErrorCodes.ExtensionNotFound,
                    message = "Extension $extensionId is not registered",
                    recoverable = true,
                ),
            )

        if (hostApiVersion !in registration.manifest.apiRange) {
            return failure(
                invocationId = invocationId,
                extensionId = extensionId,
                hook = hook,
                error = incompatibleApiError(registration.manifest),
            )
        }

        val declaration = registration.manifest.hooks.singleOrNull { candidate -> candidate.hook == hook }
            ?: return failure(
                invocationId = invocationId,
                extensionId = extensionId,
                hook = hook,
                error = ExtensionError(
                    code = ExtensionErrorCodes.HookNotDeclared,
                    message = "Extension $extensionId does not declare hook $hook",
                    recoverable = false,
                ),
            )

        val missingCapabilities = declaration.requiredCapabilities - grantedCapabilities
        if (missingCapabilities.isNotEmpty()) {
            return failure(
                invocationId = invocationId,
                extensionId = extensionId,
                hook = hook,
                error = ExtensionError(
                    code = ExtensionErrorCodes.CapabilityDenied,
                    message = "Invocation is missing required capabilities",
                    recoverable = true,
                    details = mapOf(
                        "missingCapabilities" to missingCapabilities.joinToString(transform = Capability::id)
                    ),
                ),
            )
        }

        val handler = registration.hooks[hook]
            ?: return failure(
                invocationId = invocationId,
                extensionId = extensionId,
                hook = hook,
                error = ExtensionError(
                    code = ExtensionErrorCodes.HookNotBound,
                    message = "Extension $extensionId did not bind hook $hook",
                    recoverable = false,
                ),
            )
        val invocation = ExtensionInvocation(
            id = invocationId,
            extensionId = extensionId,
            hook = hook,
            grantedCapabilities = grantedCapabilities,
            payload = payload,
        )

        val outcome = try {
            handler.invoke(invocation)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            ExtensionHookOutcome.Failure(
                ExtensionError(
                    code = ExtensionErrorCodes.InvocationFailed,
                    message = exception.message ?: "Extension invocation failed",
                    recoverable = true,
                    details = mapOf("exception" to exception.javaClass.simpleName),
                )
            )
        }
        return ExtensionResult(
            invocationId = invocationId,
            extensionId = extensionId,
            hook = hook,
            outcome = outcome,
        )
    }

    private fun incompatibleApiError(manifest: ExtensionManifest): ExtensionError = ExtensionError(
        code = ExtensionErrorCodes.ApiIncompatible,
        message = "Host API $hostApiVersion is incompatible with extension ${manifest.id}",
        recoverable = false,
        details = mapOf(
            "hostApiVersion" to hostApiVersion.toString(),
            "minimumApiVersion" to manifest.apiRange.minimum.toString(),
            "maximumApiVersion" to manifest.apiRange.maximum.toString(),
        ),
    )

    private fun invalidRegistration(
        manifest: ExtensionManifest,
        message: String,
        hooks: Set<Hook>,
    ): ExtensionRegistrationResult.Rejected = ExtensionRegistrationResult.Rejected(
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

    private fun failure(
        invocationId: InvocationId,
        extensionId: ExtensionId,
        hook: Hook,
        error: ExtensionError,
    ): ExtensionResult = ExtensionResult(
        invocationId = invocationId,
        extensionId = extensionId,
        hook = hook,
        outcome = ExtensionHookOutcome.Failure(error),
    )

    private data class Registration(
        val manifest: ExtensionManifest,
        val hooks: Map<Hook, ExtensionHook>,
    ) {
        fun asPublicModel(): RegisteredExtension = RegisteredExtension(
            manifest = manifest,
            boundHooks = hooks.keys,
        )
    }
}
