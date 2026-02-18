package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.logger.Profiles
import com.m3u.core.architecture.logger.install
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodically refreshes all M3U and Xtream playlists in the background.
 *
 * Runs every [REFRESH_INTERVAL_HOURS] hours when the device has a network connection.
 * Playlists that were refreshed recently (within the same window) are skipped via the
 * [PlaylistRepository.refresh] call, which internally delegates to
 * [com.m3u.business.playlist.PlaylistViewModel]'s timestamp-gating logic — but the
 * real guard here is [PlaylistRepository.refresh] → [SubscriptionWorker], so we keep
 * our own lightweight age-check to avoid enqueuing redundant workers.
 */
@HiltWorker
class M3URefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    delegate: Logger
) : CoroutineWorker(context, params) {

    private val logger = delegate.install(Profiles.WORKER_SUBSCRIPTION)

    override suspend fun doWork(): Result {
        return try {
            val now = System.currentTimeMillis()
            val minAgeMs = REFRESH_INTERVAL_HOURS * 60 * 60 * 1000L

            val playlists = playlistRepository.getAll().filter { playlist ->
                // Only M3U and Xtream; EPG is handled separately in AppViewModel
                val isM3UOrXtream = playlist.source == DataSource.M3U ||
                        playlist.source == DataSource.Xtream
                // Skip playlists refreshed within the current window
                val isStale = (now - playlist.lastRefreshedAt) >= minAgeMs
                isM3UOrXtream && isStale
            }

            playlists.forEach { playlist ->
                try {
                    playlistRepository.refresh(playlist.url)
                } catch (e: Exception) {
                    logger.log(e)
                    // Don't abort the whole run if one playlist fails
                }
            }

            Result.success()
        } catch (e: Exception) {
            logger.log(e)
            Result.retry()
        }
    }

    companion object {
        const val REFRESH_INTERVAL_HOURS = 6L
        private const val WORK_NAME = "m3u_background_refresh"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<M3URefreshWorker>(
                REFRESH_INTERVAL_HOURS, TimeUnit.HOURS
            )
                .setInitialDelay(REFRESH_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
