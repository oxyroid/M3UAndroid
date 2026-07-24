package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.repository.BackupStagingFiles
import com.m3u.data.repository.provider.ProviderOperationException
import com.m3u.data.repository.provider.ProviderSessionCleanupResult
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.repository.extension.ExtensionContributionRepository
import com.m3u.data.repository.extension.ExtensionContributionRunCoordinator
import com.m3u.data.repository.extension.EXTENSION_CONTRIBUTION_INPUT_WORK_KEY
import com.m3u.data.repository.extension.extensionContributionWorkKey
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import com.m3u.extension.api.BackgroundTaskRequest
import com.m3u.extension.api.BackgroundTaskResult
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.HookResult
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.runtime.ExtensionRuntime
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

@HiltWorker
class ProviderRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SubscriptionProviderRepository,
    private val pluginRepository: ExtensionPluginRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString(INPUT_PLAYLIST_URL) ?: return Result.failure()
        val reason = inputData.providerRefreshReasonOrNull() ?: return Result.failure()
        return try {
            setProgress(workDataOf(OUTPUT_PROGRESS to 0))
            pluginRepository.restoreEnabled()
            val refreshed = repository.refresh(
                playlistUrl,
                reason = reason,
            )
            setProgress(workDataOf(OUTPUT_PROGRESS to 100))
            Result.success(workDataOf(OUTPUT_CHANNEL_COUNT to refreshed.channelCount))
        } catch (exception: ProviderOperationException) {
            if (exception.recoverable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    exception.code?.let { code -> workDataOf(OUTPUT_ERROR_CODE to code) }
                        ?: workDataOf()
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        internal const val INPUT_PLAYLIST_URL = "playlist-url"
        internal const val INPUT_REFRESH_REASON = "refresh-reason"
        private const val OUTPUT_CHANNEL_COUNT = "channel-count"
        private const val OUTPUT_PROGRESS = "progress"
        private const val OUTPUT_ERROR_CODE = "error-code"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(
            workManager: WorkManager,
            playlistUrl: String,
            reason: SubscriptionRefreshReason,
        ) {
            val request = OneTimeWorkRequestBuilder<ProviderRefreshWorker>()
                .setInputData(providerRefreshInputData(playlistUrl, reason))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .addTag(SubscriptionWorker.TAG)
                .addTag(playlistUrl)
                .build()
            workManager.enqueueUniqueWork(
                "provider-refresh:$playlistUrl",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

internal fun providerRefreshInputData(
    playlistUrl: String,
    reason: SubscriptionRefreshReason,
): Data = workDataOf(
    ProviderRefreshWorker.INPUT_PLAYLIST_URL to playlistUrl,
    ProviderRefreshWorker.INPUT_REFRESH_REASON to reason.value,
)

internal fun Data.providerRefreshReasonOrNull(): SubscriptionRefreshReason? =
    getString(ProviderRefreshWorker.INPUT_REFRESH_REASON)?.let { value ->
        runCatching { SubscriptionRefreshReason(value) }.getOrNull()
    }

@HiltWorker
class ProviderSessionCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SubscriptionProviderRepository,
    private val pluginRepository: ExtensionPluginRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val afterCreatedAtEpochMillis = inputData
            .getLong(INPUT_AFTER_CREATED_AT_EPOCH_MILLIS, INVALID_CURSOR)
            .takeIf { value -> value >= 0L }
        val afterSessionId = inputData.getString(INPUT_AFTER_SESSION_ID)
        if ((afterCreatedAtEpochMillis == null) != (afterSessionId == null)) {
            return Result.failure()
        }
        return try {
            pluginRepository.restoreEnabled()
            val cleanup = repository.closeOrphanedPlaybackSessions(
                afterCreatedAtEpochMillis = afterCreatedAtEpochMillis,
                afterSessionId = afterSessionId,
            )
            if (cleanup.recoverablePendingCount > 0 && runAttemptCount < MAX_ATTEMPTS) {
                return Result.retry()
            }
            cleanup.enqueueContinuationIfPresent(
                workManager = WorkManager.getInstance(applicationContext),
            )
            cleanup.toTerminalWorkerResult()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: ProviderOperationException) {
            if (exception.recoverable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val INPUT_AFTER_CREATED_AT_EPOCH_MILLIS =
            "after-created-at-epoch-millis"
        private const val INPUT_AFTER_SESSION_ID = "after-session-id"
        private const val INVALID_CURSOR = -1L
        private const val MAX_ATTEMPTS = 3
        private const val OUTPUT_CLOSED_SESSION_COUNT = "closed-session-count"
        private const val OUTPUT_PENDING_SESSION_COUNT = "pending-session-count"

        fun enqueue(workManager: WorkManager) {
            val request = request()
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueueRetry(workManager: WorkManager) {
            // Failed close acknowledgement can happen while startup cleanup is running.
            // Keep the retry independent so a newly retained tombstone gets another scan.
            workManager.enqueue(request())
        }

        private fun enqueueContinuation(
            workManager: WorkManager,
            afterCreatedAtEpochMillis: Long,
            afterSessionId: String,
        ) {
            // A page must not depend on the previous page: permanent failures in an older
            // session must not starve newer sessions.
            workManager.enqueue(
                request(
                    afterCreatedAtEpochMillis = afterCreatedAtEpochMillis,
                    afterSessionId = afterSessionId,
                ),
            )
        }

        private fun request(
            afterCreatedAtEpochMillis: Long? = null,
            afterSessionId: String? = null,
        ) = run {
            require((afterCreatedAtEpochMillis == null) == (afterSessionId == null))
            val input = Data.Builder()
                .apply {
                    if (afterCreatedAtEpochMillis != null && afterSessionId != null) {
                        putLong(
                            INPUT_AFTER_CREATED_AT_EPOCH_MILLIS,
                            afterCreatedAtEpochMillis,
                        )
                        putString(INPUT_AFTER_SESSION_ID, afterSessionId)
                    }
                }
                .build()
            OneTimeWorkRequestBuilder<ProviderSessionCleanupWorker>()
                .setInputData(input)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag(WORK_TAG)
                .build()
        }

        internal const val WORK_TAG = "provider-session-cleanup"
        private const val WORK_NAME = WORK_TAG
    }

    private fun ProviderSessionCleanupResult.toTerminalWorkerResult(): Result = when {
        pendingCount == 0 -> Result.success(
            workDataOf(OUTPUT_CLOSED_SESSION_COUNT to closedCount)
        )
        else -> Result.failure(workDataOf(OUTPUT_PENDING_SESSION_COUNT to pendingCount))
    }

    private fun ProviderSessionCleanupResult.enqueueContinuationIfPresent(
        workManager: WorkManager,
    ) {
        val afterCreatedAt = continuationCreatedAtEpochMillis ?: return
        val afterSession = checkNotNull(continuationSessionId)
        enqueueContinuation(
            workManager = workManager,
            afterCreatedAtEpochMillis = afterCreatedAt,
            afterSessionId = afterSession,
        )
    }
}

@HiltWorker
class ProviderCredentialRecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SubscriptionProviderRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        BackupStagingFiles.cleanup(applicationContext.cacheDir)
        val invalidatedCount = repository.invalidateUndecryptableCredentials()
        Result.success(workDataOf(OUTPUT_INVALIDATED_ACCOUNT_COUNT to invalidatedCount))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val OUTPUT_INVALIDATED_ACCOUNT_COUNT = "invalidated-account-count"

        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                "provider-credential-recovery",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ProviderCredentialRecoveryWorker>().build(),
            )
        }
    }
}

@HiltWorker
class ExtensionPluginBootstrapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ExtensionPluginRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        repository.restoreEnabled()
        Result.success()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                "extension-plugin-bootstrap",
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ExtensionPluginBootstrapWorker>().build(),
            )
        }
    }
}

@HiltWorker
internal class ExtensionContributionRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pluginRepository: ExtensionPluginRepository,
    private val contributions: ExtensionContributionRepository,
    private val importer: SubscriptionProviderImporter,
    private val runCoordinator: ExtensionContributionRunCoordinator,
    private val playlistDao: PlaylistDao,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val workKey = inputData.getString(EXTENSION_CONTRIBUTION_INPUT_WORK_KEY)
            ?.takeIf { key -> key.length == SHA_256_HEX_LENGTH && key.all(Char::isLowerHexDigit) }
            ?: return Result.failure()
        val playlistUrl = playlistDao.getAll()
            .asSequence()
            .map { playlist -> playlist.url }
            .filter(String::isNotBlank)
            .singleOrNull { url -> extensionContributionWorkKey(url) == workKey }
            ?: return Result.success()
        return try {
            runCoordinator.withPlaylist(playlistUrl) {
                pluginRepository.restoreEnabled()
                setProgress(workDataOf(OUTPUT_PROGRESS to 10))
                val snapshots = importer.metadataSnapshots(playlistUrl)
                val metadataRefreshGeneration =
                    importer.captureExtensionMetadataRefreshGeneration()
                val metadata = contributions.enrichChannels(
                    channels = snapshots,
                    playlistUrl = playlistUrl,
                )
                val importedMetadataCount = importer.applyMetadataEnrichment(
                    playlistUrl = playlistUrl,
                    refreshes = metadata,
                    refreshGeneration = metadataRefreshGeneration,
                )
                setProgress(workDataOf(OUTPUT_PROGRESS to 50))

                val now = System.currentTimeMillis()
                val epgRefreshGeneration = importer.captureExtensionEpgRefreshGeneration()
                val epgRefreshes = contributions.refreshEpg(
                    channelReferences = snapshots.map { snapshot -> snapshot.stableReference },
                    fromEpochMillis = now - EPG_HISTORY_MILLIS,
                    toEpochMillis = now + EPG_FUTURE_MILLIS,
                    playlistUrl = playlistUrl,
                )
                val importedProgrammeCount = importer.replaceExtensionEpg(
                    playlistUrl = playlistUrl,
                    refreshes = epgRefreshes,
                    refreshGeneration = epgRefreshGeneration,
                )
                setProgress(workDataOf(OUTPUT_PROGRESS to 100))
                Result.success(
                    workDataOf(
                        OUTPUT_METADATA_COUNT to importedMetadataCount,
                        OUTPUT_PROGRAMME_COUNT to importedProgrammeCount,
                    )
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            extensionContributionFailureResult(runAttemptCount)
        }
    }

    companion object {
        private const val OUTPUT_PROGRESS = "progress"
        private const val OUTPUT_METADATA_COUNT = "metadata-count"
        private const val OUTPUT_PROGRAMME_COUNT = "programme-count"
        private const val EPG_HISTORY_MILLIS = 24L * 60 * 60 * 1_000
        private const val EPG_FUTURE_MILLIS = 7L * 24 * 60 * 60 * 1_000
        private const val SHA_256_HEX_LENGTH = 64

    }
}

internal fun extensionContributionFailureResult(
    runAttemptCount: Int,
): ListenableWorker.Result =
    if (runAttemptCount < EXTENSION_CONTRIBUTION_MAX_ATTEMPTS) {
        ListenableWorker.Result.retry()
    } else {
        ListenableWorker.Result.failure()
    }

private const val EXTENSION_CONTRIBUTION_MAX_ATTEMPTS = 3

private fun Char.isLowerHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f'

@HiltWorker
class ExtensionBackgroundTaskWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runtime: ExtensionRuntime,
    private val pluginRepository: ExtensionPluginRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val extensionId = inputData.getString(INPUT_EXTENSION_ID)
            ?.let { value -> runCatching { ExtensionId(value) }.getOrNull() }
            ?: return Result.failure()
        val taskId = inputData.getString(INPUT_TASK_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        return try {
            pluginRepository.restoreEnabled()
            backgroundTaskWorkResult(
                outcome = runtime.invoke(
                    extensionId,
                    HostHookSpecs.BackgroundTask,
                    BackgroundTaskRequest(taskId = taskId, runAttempt = runAttemptCount),
                ).outcome,
                runAttemptCount = runAttemptCount,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < MAX_BACKGROUND_TASK_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        internal const val INPUT_EXTENSION_ID = "extension-id"
        internal const val INPUT_TASK_ID = "task-id"
    }
}

internal fun backgroundTaskWorkResult(
    outcome: HookResult<BackgroundTaskResult>,
    runAttemptCount: Int,
): ListenableWorker.Result = when (outcome) {
    is HookResult.Success -> ListenableWorker.Result.success()
    is HookResult.Failure -> if (
        outcome.error.recoverable && runAttemptCount < MAX_BACKGROUND_TASK_ATTEMPTS
    ) {
        ListenableWorker.Result.retry()
    } else {
        ListenableWorker.Result.failure(
            workDataOf(BACKGROUND_TASK_OUTPUT_ERROR_CODE to outcome.error.code.value)
        )
    }
}

private const val BACKGROUND_TASK_OUTPUT_ERROR_CODE = "error-code"
private const val MAX_BACKGROUND_TASK_ATTEMPTS = 3
