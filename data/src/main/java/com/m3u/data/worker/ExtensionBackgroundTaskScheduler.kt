package com.m3u.data.worker

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.m3u.extension.api.ExtensionBackgroundTaskDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.HostHookSpecs
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ExtensionBackgroundTaskScheduler internal constructor(
    private val workOperations: ExtensionBackgroundWorkOperations,
) {
    @Inject
    constructor(workManager: WorkManager) : this(
        WorkManagerExtensionBackgroundWorkOperations(workManager)
    )

    suspend fun reconcile(
        manifest: ExtensionManifest,
        enabled: Boolean,
        grantedCapabilities: Set<String>,
    ) {
        val requiredCapabilities = manifest.hooks
            .singleOrNull { declaration ->
                declaration.hook == HostHookSpecs.BackgroundTask.hook
            }
            ?.requiredCapabilities
            ?.mapTo(mutableSetOf()) { capability -> capability.id }
            .orEmpty()
        if (!enabled || !grantedCapabilities.containsAll(requiredCapabilities)) {
            cancel(manifest.id)
            return
        }

        val extensionTag = extensionBackgroundWorkTag(manifest.id)
        val desiredWork = manifest.backgroundTasks.associate { declaration ->
            extensionBackgroundWorkName(manifest.id, declaration.taskId) to
                extensionBackgroundWorkRequest(manifest.id, declaration)
        }
        val existingWorkNames = workOperations.scheduledWorkTags(extensionTag)
            .flatten()
            .filterTo(mutableSetOf()) { tag -> tag.startsWith("$extensionTag:") }

        (existingWorkNames - desiredWork.keys).forEach { uniqueWorkName ->
            workOperations.cancelUniqueWork(uniqueWorkName)
        }
        desiredWork.forEach { (uniqueWorkName, request) ->
            workOperations.enqueueUniquePeriodicWork(
                uniqueWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    suspend fun cancel(extensionId: ExtensionId) {
        workOperations.cancelAllWorkByTag(extensionBackgroundWorkTag(extensionId))
    }
}

internal interface ExtensionBackgroundWorkOperations {
    suspend fun scheduledWorkTags(tag: String): List<Set<String>>

    suspend fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    )

    suspend fun cancelUniqueWork(uniqueWorkName: String)

    suspend fun cancelAllWorkByTag(tag: String)
}

private class WorkManagerExtensionBackgroundWorkOperations(
    private val workManager: WorkManager,
) : ExtensionBackgroundWorkOperations {
    override suspend fun scheduledWorkTags(tag: String): List<Set<String>> =
        workManager.getWorkInfosByTagFlow(tag).first().map { workInfo -> workInfo.tags }

    override suspend fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    ) {
        workManager.enqueueUniquePeriodicWork(uniqueWorkName, policy, request).await()
    }

    override suspend fun cancelUniqueWork(uniqueWorkName: String) {
        workManager.cancelUniqueWork(uniqueWorkName).await()
    }

    override suspend fun cancelAllWorkByTag(tag: String) {
        workManager.cancelAllWorkByTag(tag).await()
    }
}

internal fun extensionBackgroundWorkRequest(
    extensionId: ExtensionId,
    declaration: ExtensionBackgroundTaskDeclaration,
): PeriodicWorkRequest {
    val extensionTag = extensionBackgroundWorkTag(extensionId)
    val uniqueWorkName = extensionBackgroundWorkName(extensionId, declaration.taskId)
    return PeriodicWorkRequestBuilder<ExtensionBackgroundTaskWorker>(
        declaration.repeatIntervalHours.toLong(),
        TimeUnit.HOURS,
    )
        .setInputData(
            workDataOf(
                ExtensionBackgroundTaskWorker.INPUT_EXTENSION_ID to extensionId.value,
                ExtensionBackgroundTaskWorker.INPUT_TASK_ID to declaration.taskId,
            )
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (declaration.requiresNetwork) {
                        NetworkType.CONNECTED
                    } else {
                        NetworkType.NOT_REQUIRED
                    }
                )
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
        .addTag(extensionTag)
        .addTag(uniqueWorkName)
        .build()
}

internal fun extensionBackgroundWorkTag(extensionId: ExtensionId): String =
    "extension-background:${extensionId.value}"

internal fun extensionBackgroundWorkName(extensionId: ExtensionId, taskId: String): String =
    "${extensionBackgroundWorkTag(extensionId)}:$taskId"
