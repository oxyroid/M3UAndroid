package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.database.dao.ChannelDao
import com.m3u.data.database.dao.PlaylistDao
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.repository.channel.ChannelDetailsRepository
import com.m3u.data.service.PlayerManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Collects the descriptions of a whole catalogue, slowly and on purpose.
 *
 * Searching by actor needs every title fetched, and Xtream gives them one
 * request at a time: some twenty-eight thousand of them here. Two constraints
 * shape the whole thing.
 *
 * These accounts commonly allow **a single connection**, shared with playback.
 * So the sweep stops the moment something starts playing and hands the
 * connection back — collecting metadata must never cost the viewer their
 * stream. It also paces itself between requests instead of going as fast as
 * the panel allows.
 *
 * Progress lives in the database rather than in the worker: each batch asks
 * for channels that still have no description, so an interrupted run resumes
 * by simply asking again. Nothing to persist, nothing to lose.
 */
@HiltWorker
class ChannelDetailsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val channelDao: ChannelDao,
    private val playlistDao: PlaylistDao,
    private val channelDetailsRepository: ChannelDetailsRepository,
    private val playerManager: PlayerManager,
) : CoroutineWorker(context, params) {
    private val timber = Timber.tag("ChannelDetailsWorker")

    override suspend fun doWork(): Result {
        val playlistUrl = inputData.getString(INPUT_STRING_PLAYLIST_URL) ?: return Result.failure()
        val playlist = playlistDao.get(playlistUrl) ?: return Result.failure()
        // Live channels carry no description; only films and series do.
        if (!playlist.isVod && !playlist.isSeries) return Result.success()

        var fetched = 0
        try {
            while (true) {
                if (playerManager.isPlaying.value) {
                    // Someone is watching. Retry rather than fail: WorkManager
                    // backs off and comes back when the box is idle again.
                    timber.d("playback in progress, yielding the connection")
                    return Result.retry()
                }
                val pending = channelDao.getWithoutDetails(playlistUrl, BATCH_SIZE)
                if (pending.isEmpty()) {
                    timber.d("catalogue complete, $fetched fetched this run")
                    return Result.success()
                }
                for (channel in pending) {
                    if (isStopped) return Result.retry()
                    if (playerManager.isPlaying.value) return Result.retry()
                    channelDetailsRepository.fetchIfMissing(channel)
                    fetched++
                    delay(REQUEST_INTERVAL_MILLIS)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.w(e, "sweep interrupted after $fetched")
            return Result.retry()
        }
    }

    companion object {
        private const val INPUT_STRING_PLAYLIST_URL = "playlist-url"
        private const val BATCH_SIZE = 50

        /**
         * Deliberately unhurried. Descriptions are worth nothing on a schedule,
         * and a burst of thousands of requests is what a panel reads as abuse.
         */
        private const val REQUEST_INTERVAL_MILLIS = 250L

        fun enqueue(workManager: WorkManager, playlistUrl: String) {
            workManager.enqueueUniqueWork(
                workName(playlistUrl),
                // Keep, not replace: re-enqueueing must not restart a sweep
                // that is already making its way through the catalogue.
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ChannelDetailsWorker>()
                    .setInputData(workDataOf(INPUT_STRING_PLAYLIST_URL to playlistUrl))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                    .addTag(playlistWorkTag(playlistUrl))
                    .build()
            )
        }

        fun cancel(workManager: WorkManager, playlistUrl: String) {
            workManager.cancelUniqueWork(workName(playlistUrl))
        }

        // Hashed, never raw: a playlist URL carries the account credentials in
        // its query string, and work names surface in diagnostics.
        private fun workName(playlistUrl: String): String =
            hashedWorkTag(namespace = "channel-details", value = playlistUrl)
    }
}
