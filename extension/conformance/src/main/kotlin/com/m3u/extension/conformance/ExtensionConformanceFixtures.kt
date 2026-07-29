package com.m3u.extension.conformance

import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCallContext
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionInvocationBudget
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingsSnapshot
import com.m3u.extension.api.HostHookSpecs
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

object ExtensionConformanceFixtures {
    const val RESET_TASK_ID = "conformance-reset"
    const val CONTEXT_TASK_ID = "conformance-context"
    const val CANCELLATION_TASK_ID = "conformance-cancel"
    const val CANCELLATION_STATUS_TASK_ID = "conformance-cancel-status"
    const val SETTING_KEY = "conformance/value"
    private const val SETTING_VALUE = "visible"
    private const val INPUT_VALUE = "round-trip"

    val Settings = ExtensionSettingsSnapshot(
        schemaVersions = mapOf("conformance" to 1),
        values = mapOf(SETTING_KEY to JsonPrimitive(SETTING_VALUE)),
    )

    val Budget = ExtensionInvocationBudget(
        remainingTimeMillis = 10_000,
        maxBrokerRequests = 2,
        maxBrokerRequestBytes = 16_384,
        maxBrokerResponseBytes = 65_536,
    )

    val ResetCase = ExtensionConformanceCase(
        name = "background fixture reset",
        spec = HostHookSpecs.BackgroundTask,
        request = BackgroundTaskRequest(RESET_TASK_ID),
        grantedCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask),
        invocationBudget = Budget,
        validateSuccess = { result, _ ->
            check(result.output["reset"] == "true")
        },
    )

    val ContextCase = ExtensionConformanceCase(
        name = "background context round trip",
        spec = HostHookSpecs.BackgroundTask,
        request = BackgroundTaskRequest(
            taskId = CONTEXT_TASK_ID,
            input = mapOf("probe" to INPUT_VALUE),
        ),
        grantedCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask),
        settings = Settings,
        invocationBudget = Budget,
        validateSuccess = { result, envelope ->
            val budget = envelope.invocationBudget
            val remainingTimeMillis = checkNotNull(
                result.output["remainingTimeMillis"]?.toLongOrNull()
            )
            check(
                result.output == mapOf(
                    "input" to INPUT_VALUE,
                    "setting" to SETTING_VALUE,
                    "grants" to ExtensionCapabilityIds.BackgroundTask.id,
                    "invocationId" to envelope.invocationId.value,
                    "extensionId" to envelope.extensionId.value,
                    "remainingTimeMillis" to remainingTimeMillis.toString(),
                    "maxBrokerRequests" to budget.maxBrokerRequests.toString(),
                    "maxBrokerRequestBytes" to budget.maxBrokerRequestBytes.toString(),
                    "maxBrokerResponseBytes" to budget.maxBrokerResponseBytes.toString(),
                )
            ) {
                "Background context was not preserved: ${result.output}"
            }
            check(remainingTimeMillis in 1..budget.remainingTimeMillis) {
                "Invocation deadline was not preserved: $remainingTimeMillis"
            }
        },
    )

    val CancellationCase = ExtensionConformanceCase(
        name = "background cancellation",
        spec = HostHookSpecs.BackgroundTask,
        request = BackgroundTaskRequest(CANCELLATION_TASK_ID),
        grantedCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask),
        invocationBudget = Budget,
    )

    val CancellationStatusCase = ExtensionConformanceCase(
        name = "background cancellation status",
        spec = HostHookSpecs.BackgroundTask,
        request = BackgroundTaskRequest(CANCELLATION_STATUS_TASK_ID),
        grantedCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask),
        invocationBudget = Budget,
    )

    fun handlerStarted(result: BackgroundTaskResult): Boolean =
        result.output["started"] == "true"

    fun handlerCancelled(result: BackgroundTaskResult): Boolean =
        result.output["cancelled"] == "true"

    fun manifest(
        id: ExtensionId,
        displayName: String,
    ): ExtensionManifest = ExtensionManifest(
        id = id,
        displayName = displayName,
        extensionVersion = ExtensionSemanticVersion(1, 0, 0, "conformance"),
        apiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        hooks = setOf(
            ExtensionHookDeclaration(
                hook = HostHookSpecs.BackgroundTask.hook,
                schemaVersion = HostHookSpecs.BackgroundTask.schemaVersion,
                requiredCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask),
            )
        ),
        capabilities = setOf(
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.BackgroundTask,
                reason = "Verify extension cancellation and invocation context",
            )
        ),
    )
}

class ExtensionConformanceFixtureState {
    private val started = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    suspend fun handle(
        request: BackgroundTaskRequest,
        context: ExtensionCallContext,
    ): BackgroundTaskResult = when (request.taskId) {
        ExtensionConformanceFixtures.RESET_TASK_ID -> {
            started.set(false)
            cancelled.set(false)
            BackgroundTaskResult(mapOf("reset" to "true"))
        }

        ExtensionConformanceFixtures.CONTEXT_TASK_ID -> BackgroundTaskResult(
            output = mapOf(
                "input" to request.input.getValue("probe"),
                "setting" to context.settings.values
                    .getValue(ExtensionConformanceFixtures.SETTING_KEY)
                    .jsonPrimitive
                    .content,
                "grants" to context.grantedCapabilities
                    .map { capability -> capability.id }
                    .sorted()
                    .joinToString(),
                "invocationId" to context.invocationId.value,
                "extensionId" to context.extensionId.value,
                "remainingTimeMillis" to context.invocationBudget
                    .remainingTimeMillis
                    .toString(),
                "maxBrokerRequests" to context.invocationBudget
                    .maxBrokerRequests
                    .toString(),
                "maxBrokerRequestBytes" to context.invocationBudget
                    .maxBrokerRequestBytes
                    .toString(),
                "maxBrokerResponseBytes" to context.invocationBudget
                    .maxBrokerResponseBytes
                    .toString(),
            )
        )

        ExtensionConformanceFixtures.CANCELLATION_TASK_ID -> {
            started.set(true)
            try {
                awaitCancellation()
            } finally {
                if (!currentCoroutineContext().isActive) cancelled.set(true)
            }
        }

        ExtensionConformanceFixtures.CANCELLATION_STATUS_TASK_ID ->
            BackgroundTaskResult(
                mapOf(
                    "started" to started.get().toString(),
                    "cancelled" to cancelled.get().toString(),
                )
            )

        else -> error("Unknown conformance task: ${request.taskId}")
    }

    fun handles(request: BackgroundTaskRequest): Boolean =
        request.taskId == ExtensionConformanceFixtures.RESET_TASK_ID ||
            request.taskId == ExtensionConformanceFixtures.CONTEXT_TASK_ID ||
            request.taskId == ExtensionConformanceFixtures.CANCELLATION_TASK_ID ||
            request.taskId == ExtensionConformanceFixtures.CANCELLATION_STATUS_TASK_ID
}
