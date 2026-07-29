package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

@HiltWorker
class PersistedUriPermissionCleanupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val workManager: WorkManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val requestedTag = inputData.getString(INPUT_PERMISSION_TAG)
            val permissionTags = requestedTag?.let(::listOf)
                ?: PersistedUriPermissionLeaseStore.ownedTags(context)
            var permissionStillNeeded = false
            permissionTags.forEach { permissionTag ->
                val lease = PersistedUriPermissionLeaseStore.snapshot(
                    context = context,
                    permissionTag = permissionTag,
                ) ?: return@forEach
                if (lease.pendingCount > 0) {
                    permissionStillNeeded = true
                    return@forEach
                }
                val unfinished = workManager
                    .getWorkInfosByTagFlow(permissionTag)
                    .first()
                    .any { workInfo -> !workInfo.state.isFinished }
                if (unfinished) {
                    permissionStillNeeded = true
                } else {
                    val uri = context.contentResolver
                        .persistedPermissionUriForTag(permissionTag)
                    val released = PersistedUriPermissionLeaseStore.releaseIfUnchanged(
                        context = context,
                        permissionTag = permissionTag,
                        generation = lease.generation,
                    ) {
                        if (uri != null) {
                            context.contentResolver.releasePersistedPermission(uri)
                        }
                    }
                    if (!released) {
                        permissionStillNeeded = true
                    }
                }
            }
            if (permissionStillNeeded) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val INPUT_PERMISSION_TAG = "permission-tag"
        private const val UNIQUE_WORK_PREFIX = "persisted-uri-cleanup:"
        private const val RECOVERY_WORK_NAME = "persisted-uri-cleanup-recovery"
        private const val INITIAL_DELAY_SECONDS = 10L
        private const val MINIMUM_BACKOFF_SECONDS = 10L

        fun enqueue(
            workManager: WorkManager,
            permissionTag: String,
        ) {
            val request =
                OneTimeWorkRequestBuilder<PersistedUriPermissionCleanupWorker>()
                    .setInputData(
                        workDataOf(INPUT_PERMISSION_TAG to permissionTag)
                    )
                    .setInitialDelay(
                        INITIAL_DELAY_SECONDS,
                        TimeUnit.SECONDS,
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        MINIMUM_BACKOFF_SECONDS,
                        TimeUnit.SECONDS,
                    )
                    .build()
            workManager.enqueueUniqueWork(
                "$UNIQUE_WORK_PREFIX$permissionTag",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueueRecovery(workManager: WorkManager) {
            val request =
                OneTimeWorkRequestBuilder<PersistedUriPermissionCleanupWorker>()
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        MINIMUM_BACKOFF_SECONDS,
                        TimeUnit.SECONDS,
                    )
                    .build()
            workManager.enqueueUniqueWork(
                RECOVERY_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
