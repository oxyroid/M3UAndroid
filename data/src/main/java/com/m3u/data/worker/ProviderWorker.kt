package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.BackoffPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.repository.provider.ProviderOperationException
import com.m3u.data.repository.provider.SubscriptionProviderRepository
import com.m3u.data.repository.plugin.ExtensionPluginRepository
import com.m3u.data.extension.SubscriptionProviderImporter
import com.m3u.data.repository.extension.ExtensionContributionRepository
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@HiltWorker
class ProviderRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SubscriptionProviderRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString(INPUT_PLAYLIST_URL) ?: return Result.failure()
        return try {
            setProgress(workDataOf(OUTPUT_PROGRESS to 0))
            val refreshed = repository.refresh(
                playlistUrl,
                reason = SubscriptionRefreshReason.Background,
            )
            setProgress(workDataOf(OUTPUT_PROGRESS to 100))
            Result.success(workDataOf(OUTPUT_CHANNEL_COUNT to refreshed.channelCount))
        } catch (exception: ProviderOperationException) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val INPUT_PLAYLIST_URL = "playlist-url"
        private const val OUTPUT_CHANNEL_COUNT = "channel-count"
        private const val OUTPUT_PROGRESS = "progress"
        private const val MAX_ATTEMPTS = 3

        fun enqueue(workManager: WorkManager, playlistUrl: String) {
            val request = OneTimeWorkRequestBuilder<ProviderRefreshWorker>()
                .setInputData(workDataOf(INPUT_PLAYLIST_URL to playlistUrl))
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

@HiltWorker
class ProviderSessionCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: SubscriptionProviderRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        repository.closeOrphanedPlaybackSessions()
        Result.success()
    } catch (exception: ProviderOperationException) {
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
    }

    companion object {
        private const val MAX_ATTEMPTS = 3

        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<ProviderSessionCleanupWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(
                "provider-session-cleanup",
                ExistingWorkPolicy.KEEP,
                request,
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
    override suspend fun doWork(): Result = runCatching { repository.restoreEnabled() }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })

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
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString(INPUT_PLAYLIST_URL) ?: return Result.failure()
        return try {
            pluginRepository.restoreEnabled()
            setProgress(workDataOf(OUTPUT_PROGRESS to 10))
            val snapshots = importer.metadataSnapshots(playlistUrl)
            val metadata = contributions.enrichChannels(snapshots)
            importer.applyMetadataEnrichment(playlistUrl, metadata)
            setProgress(workDataOf(OUTPUT_PROGRESS to 50))

            val now = System.currentTimeMillis()
            val epgRefreshGeneration = importer.captureExtensionEpgRefreshGeneration()
            val epgRefreshes = contributions.refreshEpg(
                channelReferences = snapshots.map { snapshot -> snapshot.stableReference },
                fromEpochMillis = now - EPG_HISTORY_MILLIS,
                toEpochMillis = now + EPG_FUTURE_MILLIS,
            )
            val importedProgrammeCount = importer.replaceExtensionEpg(
                playlistUrl = playlistUrl,
                refreshes = epgRefreshes,
                refreshGeneration = epgRefreshGeneration,
            )
            setProgress(workDataOf(OUTPUT_PROGRESS to 100))
            Result.success(
                workDataOf(
                    OUTPUT_METADATA_COUNT to metadata.size,
                    OUTPUT_PROGRAMME_COUNT to importedProgrammeCount,
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val INPUT_PLAYLIST_URL = "playlist-url"
        private const val OUTPUT_PROGRESS = "progress"
        private const val OUTPUT_METADATA_COUNT = "metadata-count"
        private const val OUTPUT_PROGRAMME_COUNT = "programme-count"
        private const val MAX_ATTEMPTS = 3
        private const val EPG_HISTORY_MILLIS = 24L * 60 * 60 * 1_000
        private const val EPG_FUTURE_MILLIS = 7L * 24 * 60 * 60 * 1_000

        fun enqueue(workManager: WorkManager, playlistUrl: String) {
            val request = OneTimeWorkRequestBuilder<ExtensionContributionRefreshWorker>()
                .setInputData(workDataOf(INPUT_PLAYLIST_URL to playlistUrl))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(
                "extension-contributions:$playlistUrl",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

@HiltWorker
class ExtensionBackgroundTaskWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runtime: ExtensionRuntime,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val extensionId = inputData.getString(INPUT_EXTENSION_ID)?.let(::ExtensionId)
            ?: return Result.failure()
        val taskId = inputData.getString(INPUT_TASK_ID) ?: return Result.failure()
        val input = inputData.getString(INPUT_JSON)?.let { encoded ->
            runCatching { json.decodeFromString<Map<String, String>>(encoded) }.getOrNull()
        } ?: emptyMap()
        return when (
            val outcome = runtime.invoke(
                extensionId,
                HostHookSpecs.BackgroundTask,
                BackgroundTaskRequest(taskId, input, runAttemptCount),
            ).outcome
        ) {
            is HookResult.Success -> outcome.payload.toWorkResult()
            is HookResult.Failure -> if (outcome.error.recoverable && runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(workDataOf(OUTPUT_ERROR_CODE to outcome.error.code.value))
            }
        }
    }

    private fun BackgroundTaskResult.toWorkResult(): Result {
        if (retryAfterMillis != null && runAttemptCount < MAX_ATTEMPTS) return Result.retry()
        val encoded = json.encodeToString(output)
        return if (encoded.encodeToByteArray().size <= MAX_OUTPUT_BYTES) {
            Result.success(workDataOf(OUTPUT_JSON to encoded))
        } else {
            Result.failure(workDataOf(OUTPUT_ERROR_CODE to "background.output_too_large"))
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private const val INPUT_EXTENSION_ID = "extension-id"
        private const val INPUT_TASK_ID = "task-id"
        private const val INPUT_JSON = "input-json"
        private const val OUTPUT_JSON = "output-json"
        private const val OUTPUT_ERROR_CODE = "error-code"
        private const val MAX_ATTEMPTS = 3
        private const val MAX_OUTPUT_BYTES = 8 * 1024

        fun enqueue(
            workManager: WorkManager,
            extensionId: ExtensionId,
            taskId: String,
            input: Map<String, String> = emptyMap(),
            requiresNetwork: Boolean = false,
        ) {
            val request = OneTimeWorkRequestBuilder<ExtensionBackgroundTaskWorker>()
                .setInputData(
                    workDataOf(
                        INPUT_EXTENSION_ID to extensionId.value,
                        INPUT_TASK_ID to taskId,
                        INPUT_JSON to json.encodeToString(input),
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
                        )
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(
                "extension-background:${extensionId.value}:$taskId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
