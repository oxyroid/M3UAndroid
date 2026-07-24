package com.m3u.data.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.workDataOf
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionBackgroundTaskDeclaration
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionError
import com.m3u.extension.api.ExtensionErrorCode
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionBackgroundTaskSchedulerTest {
    @Test
    fun reconcileReplacesStaleDeclarationsAndBuildsBoundedPeriodicRequests() = runBlocking {
        val extensionId = ExtensionId("com.example.background")
        val staleName = extensionBackgroundWorkName(extensionId, "stale")
        val operations = RecordingBackgroundWorkOperations(
            existingTags = listOf(
                setOf(extensionBackgroundWorkTag(extensionId), staleName)
            )
        )
        val scheduler = ExtensionBackgroundTaskScheduler(operations)

        scheduler.reconcile(
            manifest(
                ExtensionBackgroundTaskDeclaration(
                    taskId = "network.refresh",
                    repeatIntervalHours = 6,
                    requiresNetwork = true,
                ),
                ExtensionBackgroundTaskDeclaration(
                    taskId = "local.cleanup",
                    repeatIntervalHours = 24,
                ),
            ),
            enabled = true,
            grantedCapabilities = setOf(
                ExtensionCapabilityIds.BackgroundTask.id,
                ExtensionCapabilityIds.Network.id,
            ),
        )

        assertEquals(listOf(staleName), operations.cancelledUniqueWork)
        assertEquals(2, operations.enqueued.size)
        assertTrue(
            operations.enqueued.all { scheduled ->
                scheduled.policy == ExistingPeriodicWorkPolicy.UPDATE
            }
        )
        val network = operations.enqueued.single { scheduled ->
            scheduled.uniqueWorkName.endsWith(":network.refresh")
        }.request
        assertEquals(
            setOf(
                ExtensionBackgroundTaskWorker.INPUT_EXTENSION_ID,
                ExtensionBackgroundTaskWorker.INPUT_TASK_ID,
            ),
            network.workSpec.input.keyValueMap.keys,
        )
        assertEquals(extensionId.value, network.workSpec.input.getString("extension-id"))
        assertEquals("network.refresh", network.workSpec.input.getString("task-id"))
        assertEquals(TimeUnit.HOURS.toMillis(6), network.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, network.workSpec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, network.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(10), network.workSpec.backoffDelayDuration)
        assertTrue(extensionBackgroundWorkTag(extensionId) in network.tags)
        assertTrue(extensionBackgroundWorkName(extensionId, "network.refresh") in network.tags)
    }

    @Test
    fun reconcileDoesNotInventWorkFromHookAndCapabilityAlone() = runBlocking {
        val extensionId = ExtensionId("com.example.background")
        val staleName = extensionBackgroundWorkName(extensionId, "removed")
        val operations = RecordingBackgroundWorkOperations(
            existingTags = listOf(setOf(extensionBackgroundWorkTag(extensionId), staleName))
        )

        ExtensionBackgroundTaskScheduler(operations).reconcile(
            manifest(),
            enabled = true,
            grantedCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask.id),
        )

        assertEquals(listOf(staleName), operations.cancelledUniqueWork)
        assertTrue(operations.enqueued.isEmpty())
    }

    @Test
    fun disabledManifestCancelsAllExtensionWork() = runBlocking {
        val operations = RecordingBackgroundWorkOperations()
        val extensionId = ExtensionId("com.example.background")

        ExtensionBackgroundTaskScheduler(operations).reconcile(
            manifest(ExtensionBackgroundTaskDeclaration("refresh", 24)),
            enabled = false,
            grantedCapabilities = emptySet(),
        )

        assertEquals(
            listOf(extensionBackgroundWorkTag(extensionId)),
            operations.cancelledTags,
        )
        assertTrue(operations.enqueued.isEmpty())
        assertTrue(operations.queriedTags.isEmpty())
    }

    @Test
    fun missingEffectiveCapabilityCancelsInsteadOfScheduling() = runBlocking {
        val operations = RecordingBackgroundWorkOperations()
        val extensionId = ExtensionId("com.example.background")

        ExtensionBackgroundTaskScheduler(operations).reconcile(
            manifest(ExtensionBackgroundTaskDeclaration("refresh", 24)),
            enabled = true,
            grantedCapabilities = emptySet(),
        )

        assertEquals(
            listOf(extensionBackgroundWorkTag(extensionId)),
            operations.cancelledTags,
        )
        assertTrue(operations.enqueued.isEmpty())
    }

    @Test
    fun networkTaskRequiresTheEffectiveNetworkGrant() = runBlocking {
        val operations = RecordingBackgroundWorkOperations()
        val extensionId = ExtensionId("com.example.background")

        ExtensionBackgroundTaskScheduler(operations).reconcile(
            manifest(
                ExtensionBackgroundTaskDeclaration(
                    taskId = "network.refresh",
                    repeatIntervalHours = 24,
                    requiresNetwork = true,
                )
            ),
            enabled = true,
            grantedCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask.id),
        )

        assertEquals(
            listOf(extensionBackgroundWorkTag(extensionId)),
            operations.cancelledTags,
        )
        assertTrue(operations.enqueued.isEmpty())
        assertTrue(operations.queriedTags.isEmpty())
    }

    @Test
    fun workManagerOperationFailureIsPropagatedToTheLifecycleOwner() = runBlocking {
        val failure = IllegalStateException("operation failed")
        val operations = RecordingBackgroundWorkOperations(enqueueFailure = failure)

        val thrown = runCatching {
            ExtensionBackgroundTaskScheduler(operations).reconcile(
                manifest(ExtensionBackgroundTaskDeclaration("refresh", 24)),
                enabled = true,
                grantedCapabilities = setOf(ExtensionCapabilityIds.BackgroundTask.id),
            )
        }.exceptionOrNull()

        assertEquals(failure, thrown)
    }

    @Test
    fun workerResultRetriesOnlyRecoverableFailuresWithinAttemptLimit() {
        assertEquals(
            ListenableWorker.Result.success(),
            backgroundTaskWorkResult(
                HookResult.Success(BackgroundTaskResult(mapOf("completed" to "true"))),
                runAttemptCount = 0,
            ),
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            backgroundTaskWorkResult(failure(recoverable = true), runAttemptCount = 0),
        )
        assertEquals(
            ListenableWorker.Result.failure(
                workDataOf("error-code" to "background.failed")
            ),
            backgroundTaskWorkResult(failure(recoverable = false), runAttemptCount = 0),
        )
        assertEquals(
            ListenableWorker.Result.failure(
                workDataOf("error-code" to "background.failed")
            ),
            backgroundTaskWorkResult(failure(recoverable = true), runAttemptCount = 3),
        )
    }

    private fun manifest(
        vararg tasks: ExtensionBackgroundTaskDeclaration,
    ): ExtensionManifest {
        val requiresNetwork = tasks.any(ExtensionBackgroundTaskDeclaration::requiresNetwork)
        val requiredCapabilities = buildSet {
            add(ExtensionCapabilityIds.BackgroundTask)
            if (requiresNetwork) add(ExtensionCapabilityIds.Network)
        }
        val capabilities = buildSet {
            add(
                ExtensionCapabilityRequest(
                    capability = ExtensionCapabilityIds.BackgroundTask,
                    reason = "Run background maintenance",
                )
            )
            if (requiresNetwork) {
                add(
                    ExtensionCapabilityRequest(
                        capability = ExtensionCapabilityIds.Network,
                        reason = "Refresh remote data",
                    )
                )
            }
        }
        return ExtensionManifest(
            id = ExtensionId("com.example.background"),
            displayName = "Background",
            extensionVersion = ExtensionSemanticVersion(1, 0, 0),
            apiRange = ExtensionApiRange(
                minimum = ExtensionApiVersions.Current,
                maximum = ExtensionApiVersions.Current,
            ),
            hooks = setOf(
                ExtensionHookDeclaration(
                    hook = HostHookSpecs.BackgroundTask.hook,
                    schemaVersion = HostHookSpecs.BackgroundTask.schemaVersion,
                    requiredCapabilities = requiredCapabilities,
                )
            ),
            capabilities = capabilities,
            backgroundTasks = tasks.toList(),
        )
    }

    private fun failure(recoverable: Boolean) = HookResult.Failure(
        ExtensionError(
            code = ExtensionErrorCode("background.failed"),
            message = "Background task failed",
            recoverable = recoverable,
        )
    )
}

private class RecordingBackgroundWorkOperations(
    private val existingTags: List<Set<String>> = emptyList(),
    private val enqueueFailure: Exception? = null,
) : ExtensionBackgroundWorkOperations {
    val queriedTags = mutableListOf<String>()
    val enqueued = mutableListOf<ScheduledPeriodicWork>()
    val cancelledUniqueWork = mutableListOf<String>()
    val cancelledTags = mutableListOf<String>()

    override suspend fun scheduledWorkTags(tag: String): List<Set<String>> {
        queriedTags += tag
        return existingTags
    }

    override suspend fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ) {
        enqueueFailure?.let { failure -> throw failure }
        enqueued += ScheduledPeriodicWork(uniqueWorkName, policy, request)
    }

    override suspend fun cancelUniqueWork(uniqueWorkName: String) {
        cancelledUniqueWork += uniqueWorkName
    }

    override suspend fun cancelAllWorkByTag(tag: String) {
        cancelledTags += tag
    }
}

private data class ScheduledPeriodicWork(
    val uniqueWorkName: String,
    val policy: ExistingPeriodicWorkPolicy,
    val request: PeriodicWorkRequest,
)
