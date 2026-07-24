package com.m3u.business.setting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class ExtensionSettingsOperationQueue(
    private val scope: CoroutineScope,
    private val onFailure: suspend (Exception) -> Unit,
) {
    private val stateLock = Any()
    private val operationJobs = mutableMapOf<String, MutableSet<Job>>()
    private val updateJobs = mutableMapOf<String, MutableSet<Job>>()

    fun launchUpdate(
        extensionId: String,
        operation: suspend () -> Unit,
    ): Job = enqueue(
        extensionId = extensionId,
        cancelPendingUpdates = false,
        trackAsUpdate = true,
        operation = operation,
    )

    fun launchOperation(
        extensionId: String,
        operation: suspend () -> Unit,
    ): Job = enqueue(
        extensionId = extensionId,
        cancelPendingUpdates = false,
        trackAsUpdate = false,
        operation = operation,
    )

    fun launchDestructive(
        extensionId: String,
        operation: suspend () -> Unit,
    ): Job = enqueue(
        extensionId = extensionId,
        cancelPendingUpdates = true,
        trackAsUpdate = false,
        operation = operation,
    )

    private fun enqueue(
        extensionId: String,
        cancelPendingUpdates: Boolean,
        trackAsUpdate: Boolean,
        operation: suspend () -> Unit,
    ): Job {
        lateinit var job: Job
        val pendingUpdates: List<Job>
        synchronized(stateLock) {
            val operationsToAwait = operationJobs[extensionId].orEmpty().toList()
            pendingUpdates = if (cancelPendingUpdates) {
                updateJobs[extensionId].orEmpty().toList()
            } else {
                emptyList()
            }
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    operationsToAwait.joinAll()
                    coroutineContext.ensureActive()
                    operation()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    onFailure(failure)
                }
            }
            operationJobs.getOrPut(extensionId, ::mutableSetOf).add(job)
            if (trackAsUpdate) {
                updateJobs.getOrPut(extensionId, ::mutableSetOf).add(job)
            }
            job.invokeOnCompletion {
                removeCompletedOperation(extensionId, job, trackAsUpdate)
            }
        }
        pendingUpdates.forEach(Job::cancel)
        job.start()
        return job
    }

    private fun removeCompletedOperation(
        extensionId: String,
        job: Job,
        trackedAsUpdate: Boolean,
    ) {
        synchronized(stateLock) {
            operationJobs[extensionId]?.let { jobs ->
                jobs.remove(job)
                if (jobs.isEmpty()) operationJobs.remove(extensionId)
            }
            if (trackedAsUpdate) {
                updateJobs[extensionId]?.let { jobs ->
                    jobs.remove(job)
                    if (jobs.isEmpty()) updateJobs.remove(extensionId)
                }
            }
        }
    }
}
