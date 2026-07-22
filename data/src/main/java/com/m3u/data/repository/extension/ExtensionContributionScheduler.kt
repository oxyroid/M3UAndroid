package com.m3u.data.repository.extension

import androidx.work.WorkManager
import com.m3u.data.worker.ExtensionContributionRefreshWorker
import javax.inject.Inject

internal interface ExtensionContributionScheduler {
    fun enqueue(playlistUrl: String)
}

internal class WorkManagerExtensionContributionScheduler @Inject constructor(
    private val workManager: WorkManager,
) : ExtensionContributionScheduler {
    override fun enqueue(playlistUrl: String) {
        ExtensionContributionRefreshWorker.enqueue(workManager, playlistUrl)
    }
}
