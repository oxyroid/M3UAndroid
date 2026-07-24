package com.m3u.data.repository.extension

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.m3u.data.worker.ExtensionContributionRefreshWorker
import com.m3u.extension.api.ExtensionState
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.runtime.ExtensionExecutionKind
import com.m3u.extension.runtime.ExtensionRuntime
import com.m3u.extension.transport.android.ExtensionTrustStore
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal interface ExtensionContributionScheduler {
    suspend fun enqueue(playlistUrl: String)

    suspend fun cancel(playlistUrl: String)
}

internal class WorkManagerExtensionContributionScheduler internal constructor(
    private val operations: ExtensionContributionWorkOperations,
    private val hasEnabledContributor: () -> Boolean = { true },
) : ExtensionContributionScheduler {
    @Inject
    constructor(
        workManager: WorkManager,
        runtime: ExtensionRuntime,
        trustStore: ExtensionTrustStore,
    ) : this(
        operations = WorkManagerExtensionContributionWorkOperations(workManager),
        hasEnabledContributor = {
            runtime.hasEnabledContributionExtension(trustStore)
        },
    )

    override suspend fun enqueue(playlistUrl: String) {
        require(playlistUrl.isNotBlank()) { "Playlist URL must not be blank" }
        require(playlistUrl.toByteArray(Charsets.UTF_8).size <= MAX_PLAYLIST_URL_BYTES) {
            "Playlist URL exceeds the contribution work input limit"
        }
        if (!hasEnabledContributor()) {
            cancel(playlistUrl)
            return
        }
        val workKey = extensionContributionWorkKey(playlistUrl)
        operations.enqueueUniquePeriodicWork(
            uniqueWorkName = extensionContributionPeriodicWorkName(workKey),
            policy = ExistingPeriodicWorkPolicy.KEEP,
            request = extensionContributionPeriodicRequest(workKey),
        )
        try {
            operations.enqueueUniqueWork(
                uniqueWorkName = extensionContributionImmediateWorkName(workKey),
                policy = ExistingWorkPolicy.REPLACE,
                request = extensionContributionImmediateRequest(workKey),
            )
        } catch (failure: Exception) {
            try {
                operations.cancelUniqueWork(extensionContributionPeriodicWorkName(workKey))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Keep the enqueue failure as the primary diagnostic.
            }
            throw failure
        }
        if (!hasEnabledContributor()) {
            cancel(playlistUrl)
        }
    }

    override suspend fun cancel(playlistUrl: String) {
        if (playlistUrl.isBlank()) return
        val workKey = extensionContributionWorkKey(playlistUrl)
        var firstFailure: Exception? = null
        listOf(
            extensionContributionImmediateWorkName(workKey),
            extensionContributionPeriodicWorkName(workKey),
        ).forEach { workName ->
            try {
                operations.cancelUniqueWork(workName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        firstFailure?.let { failure -> throw failure }
    }
}

private fun ExtensionRuntime.hasEnabledContributionExtension(
    trustStore: ExtensionTrustStore,
): Boolean = listOf(
    HostHookSpecs.MetadataEnrichment.hook,
    HostHookSpecs.EpgRefresh.hook,
).any { hook ->
    extensionsSupporting(hook).any { extension ->
        extension.state == ExtensionState.ENABLED &&
            when (extension.executionKind) {
                ExtensionExecutionKind.BUILT_IN -> true
                ExtensionExecutionKind.EXTERNAL -> {
                    val grantedCapabilities =
                        trustStore.grantedCapabilities(extension.manifest.id.value)
                    extension.manifest.hooks
                        .single { declaration -> declaration.hook == hook }
                        .requiredCapabilities
                        .all { capability -> capability.id in grantedCapabilities }
                }
            }
    }
}

internal interface ExtensionContributionWorkOperations {
    suspend fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    )

    suspend fun enqueueUniquePeriodicWork(
        uniqueWorkName: String,
        policy: ExistingPeriodicWorkPolicy,
        request: PeriodicWorkRequest,
    )

    suspend fun cancelUniqueWork(uniqueWorkName: String)
}

private class WorkManagerExtensionContributionWorkOperations(
    private val workManager: WorkManager,
) : ExtensionContributionWorkOperations {
    override suspend fun enqueueUniqueWork(
        uniqueWorkName: String,
        policy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ) {
        workManager.enqueueUniqueWork(uniqueWorkName, policy, request).await()
    }

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
}

internal fun extensionContributionImmediateRequest(
    workKey: String,
): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ExtensionContributionRefreshWorker>()
    .setInputData(workDataOf(EXTENSION_CONTRIBUTION_INPUT_WORK_KEY to workKey))
    .setConstraints(extensionContributionConstraints())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
    .addTag(extensionContributionWorkTag(workKey))
    .build()

internal fun extensionContributionPeriodicRequest(
    workKey: String,
): PeriodicWorkRequest = PeriodicWorkRequestBuilder<ExtensionContributionRefreshWorker>(
    EXTENSION_CONTRIBUTION_REFRESH_INTERVAL_HOURS,
    TimeUnit.HOURS,
)
    .setInitialDelay(
        EXTENSION_CONTRIBUTION_REFRESH_INTERVAL_HOURS,
        TimeUnit.HOURS,
    )
    .setInputData(workDataOf(EXTENSION_CONTRIBUTION_INPUT_WORK_KEY to workKey))
    .setConstraints(extensionContributionConstraints())
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
    .addTag(extensionContributionWorkTag(workKey))
    .build()

private fun extensionContributionConstraints(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

internal fun extensionContributionWorkKey(playlistUrl: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(playlistUrl.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

internal fun extensionContributionImmediateWorkName(workKey: String): String =
    "extension-contributions:immediate:$workKey"

internal fun extensionContributionPeriodicWorkName(workKey: String): String =
    "extension-contributions:periodic:$workKey"

private fun extensionContributionWorkTag(workKey: String): String =
    "extension-contributions:$workKey"

internal const val EXTENSION_CONTRIBUTION_INPUT_WORK_KEY = "playlist-key"
private const val EXTENSION_CONTRIBUTION_REFRESH_INTERVAL_HOURS = 24L
private const val MAX_PLAYLIST_URL_BYTES = 8 * 1024
