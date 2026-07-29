package com.m3u.extension.conformance

import com.m3u.extension.api.Capability
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionContractCatalog
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionErrorCodes
import com.m3u.extension.api.ExtensionInvocationBudget
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionPayload
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HookSpec
import com.m3u.extension.api.InvocationId
import com.m3u.extension.api.SerializedExtensionEnvelope
import com.m3u.extension.api.SerializedExtensionResult
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Raw invocation boundary used by the shared host/extension conformance suite.
 *
 * Implementations must be safe for concurrent calls. [invoke] must treat the envelope's settings,
 * grants, invocation budget, and invocation id as values for that invocation only. [cancel] must
 * affect only the matching invocation and must propagate cancellation to the running handler.
 */
interface ExtensionConformanceDriver {
    val manifest: ExtensionManifest

    suspend fun invoke(envelope: SerializedExtensionEnvelope): SerializedExtensionResult

    suspend fun cancel(invocationId: InvocationId)
}

data class ExtensionConformanceCase<
    Request : ExtensionPayload,
    Response : ExtensionPayload,
>(
    val name: String,
    val spec: HookSpec<Request, Response>,
    val request: Request,
    val grantedCapabilities: Set<Capability>,
    val settings: ExtensionSettingsSnapshot = ExtensionSettingsSnapshot(),
    val invocationBudget: ExtensionInvocationBudget? = null,
    val validateSuccess: (Response, SerializedExtensionEnvelope) -> Unit = { _, _ -> },
)

class ExtensionConformanceSuite(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    },
    private val invocationTimeoutMillis: Long = DEFAULT_INVOCATION_TIMEOUT_MILLIS,
) {
    init {
        require(invocationTimeoutMillis > 0) {
            "Conformance invocation timeout must be positive"
        }
    }

    suspend fun <
        Request : ExtensionPayload,
        Response : ExtensionPayload,
    > verifySuccessfulInvocation(
        driver: ExtensionConformanceDriver,
        case: ExtensionConformanceCase<Request, Response>,
    ): Response {
        verifyManifest(driver.manifest, case)
        val envelope = case.envelope(driver.manifest, nextInvocationId(), json)
        val result = invokeWithinTimeout(driver, envelope, case.name)
        result.requireCorrelatedWith(envelope)
        requireConformance(result.error == null) {
            "${case.name} failed with ${result.error?.code}: ${result.error?.message}"
        }
        val payload = requireNotNull(result.payload) {
            "${case.name} returned neither a successful payload nor an error"
        }
        val decoded = runCatching {
            json.decodeFromJsonElement(case.spec.responseSerializer, payload)
        }.getOrElse { failure ->
            throw ExtensionConformanceFailure(
                "${case.name} returned an incompatible payload",
                failure,
            )
        }
        runCatching {
            case.validateSuccess(decoded, envelope)
        }.getOrElse { failure ->
            throw ExtensionConformanceFailure(
                "${case.name} returned a semantically invalid result",
                failure,
            )
        }
        return decoded
    }

    suspend fun <
        Request : ExtensionPayload,
        Response : ExtensionPayload,
    > verifyMissingRequiredCapability(
        driver: ExtensionConformanceDriver,
        case: ExtensionConformanceCase<Request, Response>,
    ) {
        val declaration = verifyManifest(driver.manifest, case)
        requireConformance(
            declaration.requiredCapabilities.all(case.grantedCapabilities::contains)
        ) {
            "${case.name} does not start with all required capabilities granted"
        }
        val capability = declaration.requiredCapabilities.firstOrNull()
            ?: throw ExtensionConformanceFailure(
                "${case.name} does not declare a required capability"
            )
        val envelope = case.copy(
            grantedCapabilities = case.grantedCapabilities - capability
        ).envelope(driver.manifest, nextInvocationId(), json)
        val result = invokeWithinTimeout(driver, envelope, case.name)
        result.requireCorrelatedWith(envelope)
        result.requireError(case.name, ExtensionErrorCodes.CapabilityDenied)
    }

    suspend fun <
        Request : ExtensionPayload,
        Response : ExtensionPayload,
    > verifySchemaMismatch(
        driver: ExtensionConformanceDriver,
        case: ExtensionConformanceCase<Request, Response>,
    ) {
        verifyManifest(driver.manifest, case)
        val envelope = case.envelope(driver.manifest, nextInvocationId(), json).copy(
            schemaVersion = case.spec.schemaVersion + 1
        )
        val result = invokeWithinTimeout(driver, envelope, case.name)
        result.requireCorrelatedWith(envelope)
        result.requireError(case.name, ExtensionErrorCodes.SchemaIncompatible)
    }

    suspend fun <
        SlowRequest : ExtensionPayload,
        SlowResponse : ExtensionPayload,
        StatusRequest : ExtensionPayload,
        StatusResponse : ExtensionPayload,
    > verifyCancellation(
        driver: ExtensionConformanceDriver,
        slowCase: ExtensionConformanceCase<SlowRequest, SlowResponse>,
        cancellationStatusCase: ExtensionConformanceCase<StatusRequest, StatusResponse>,
        handlerStarted: (StatusResponse) -> Boolean,
        handlerCancelled: (StatusResponse) -> Boolean,
    ) = supervisorScope {
        verifyManifest(driver.manifest, slowCase)
        val envelope = slowCase.envelope(driver.manifest, nextInvocationId(), json)
        val invocation = async { driver.invoke(envelope) }
        awaitCancellationState(
            driver = driver,
            statusCase = cancellationStatusCase,
            description = "${slowCase.name} did not start",
            predicate = handlerStarted,
        )
        driver.cancel(envelope.invocationId)
        val completion = runCatching {
            withTimeout(CANCELLATION_COMPLETION_TIMEOUT_MILLIS) {
                invocation.await()
            }
        }
        val failure = completion.exceptionOrNull()
        if (failure is TimeoutCancellationException) {
            invocation.cancel()
            throw ExtensionConformanceFailure(
                "${slowCase.name} did not stop after cancellation",
                failure,
            )
        }
        if (failure == null) {
            val result = checkNotNull(completion.getOrNull())
            result.requireCorrelatedWith(envelope)
            requireConformance(result.error != null) {
                "${slowCase.name} returned a successful result after cancellation"
            }
        } else {
            requireConformance(failure is CancellationException) {
                "${slowCase.name} failed with a non-cancellation exception: $failure"
            }
        }
        awaitCancellationState(
            driver = driver,
            statusCase = cancellationStatusCase,
            description = "${slowCase.name} handler did not observe cancellation",
            predicate = handlerCancelled,
        )
    }

    suspend fun verifyStandardBehavior(driver: ExtensionConformanceDriver) {
        verifySuccessfulInvocation(driver, ExtensionConformanceFixtures.ResetCase)
        verifySuccessfulInvocation(driver, ExtensionConformanceFixtures.ContextCase)
        verifyMissingRequiredCapability(driver, ExtensionConformanceFixtures.ContextCase)
        verifySchemaMismatch(driver, ExtensionConformanceFixtures.ContextCase)
        verifyCancellation(
            driver = driver,
            slowCase = ExtensionConformanceFixtures.CancellationCase,
            cancellationStatusCase = ExtensionConformanceFixtures.CancellationStatusCase,
            handlerStarted = ExtensionConformanceFixtures::handlerStarted,
            handlerCancelled = ExtensionConformanceFixtures::handlerCancelled,
        )
    }

    private suspend fun invokeWithinTimeout(
        driver: ExtensionConformanceDriver,
        envelope: SerializedExtensionEnvelope,
        caseName: String,
    ): SerializedExtensionResult = try {
        withTimeout(invocationTimeoutMillis) {
            driver.invoke(envelope)
        }
    } catch (timeout: TimeoutCancellationException) {
        throw ExtensionConformanceFailure(
            "$caseName did not complete within ${invocationTimeoutMillis}ms",
            timeout,
        )
    }

    private suspend fun <
        StatusRequest : ExtensionPayload,
        StatusResponse : ExtensionPayload,
    > awaitCancellationState(
        driver: ExtensionConformanceDriver,
        statusCase: ExtensionConformanceCase<StatusRequest, StatusResponse>,
        description: String,
        predicate: (StatusResponse) -> Boolean,
    ) {
        try {
            withTimeout(CANCELLATION_STATE_TIMEOUT_MILLIS) {
                while (true) {
                    if (predicate(verifySuccessfulInvocation(driver, statusCase))) return@withTimeout
                    delay(CANCELLATION_STATE_POLL_MILLIS)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw ExtensionConformanceFailure(description, timeout)
        }
    }

    private fun <
        Request : ExtensionPayload,
        Response : ExtensionPayload,
    > verifyManifest(
        manifest: ExtensionManifest,
        case: ExtensionConformanceCase<Request, Response>,
    ) = manifest.hooks.singleOrNull { declaration -> declaration.hook == case.spec.hook }
        ?.also { declaration ->
            requireConformance(ExtensionContractCatalog.containsCanonical(case.spec)) {
                "${case.name} does not use a canonical HookSpec"
            }
            requireConformance(declaration.schemaVersion == case.spec.schemaVersion) {
                "${case.name} manifest schema does not match its HookSpec"
            }
            requireConformance(
                declaration.requiredCapabilities.all { capability ->
                    manifest.capabilities.any { request -> request.capability == capability }
                }
            ) {
                "${case.name} manifest does not request every required capability"
            }
        }
        ?: throw ExtensionConformanceFailure(
            "${case.name} Hook is missing from ${manifest.id}"
        )

    private fun SerializedExtensionResult.requireCorrelatedWith(
        envelope: SerializedExtensionEnvelope,
    ) {
        requireConformance(invocationId == envelope.invocationId) {
            "Result invocation ID does not match its request"
        }
        requireConformance(extensionId == envelope.extensionId) {
            "Result extension ID does not match its request"
        }
        requireConformance(hook == envelope.hook) {
            "Result Hook does not match its request"
        }
        requireConformance(schemaVersion == envelope.schemaVersion) {
            "Result schema does not match its request"
        }
    }

    private fun SerializedExtensionResult.requireError(
        caseName: String,
        expectedCode: ExtensionErrorCode,
    ) {
        val actual = error
        requireConformance(actual?.code == expectedCode) {
            "$caseName returned ${actual?.code}; expected $expectedCode"
        }
        requireConformance(payload == null) {
            "$caseName returned both an error and a successful payload"
        }
    }

    private fun nextInvocationId(): InvocationId =
        InvocationId("conformance-${INVOCATION_NUMBER.incrementAndGet()}")

    private companion object {
        const val DEFAULT_INVOCATION_TIMEOUT_MILLIS = 5_000L
        const val CANCELLATION_COMPLETION_TIMEOUT_MILLIS = 5_000L
        const val CANCELLATION_STATE_TIMEOUT_MILLIS = 5_000L
        const val CANCELLATION_STATE_POLL_MILLIS = 50L
        val INVOCATION_NUMBER = AtomicLong()
    }
}

private fun <
    Request : ExtensionPayload,
    Response : ExtensionPayload,
> ExtensionConformanceCase<Request, Response>.envelope(
    manifest: ExtensionManifest,
    invocationId: InvocationId,
    json: Json,
): SerializedExtensionEnvelope = SerializedExtensionEnvelope(
    apiVersion = ExtensionApiVersions.Current,
    invocationId = invocationId,
    extensionId = manifest.id,
    hook = spec.hook,
    schemaVersion = spec.schemaVersion,
    payload = json.encodeToJsonElement(spec.requestSerializer, request),
    settings = settings,
    grantedCapabilities = grantedCapabilities,
    invocationBudget = invocationBudget,
)

private inline fun requireConformance(
    condition: Boolean,
    lazyMessage: () -> String,
) {
    if (!condition) throw ExtensionConformanceFailure(lazyMessage())
}

class ExtensionConformanceFailure(
    message: String,
    cause: Throwable? = null,
) : AssertionError(message, cause)
