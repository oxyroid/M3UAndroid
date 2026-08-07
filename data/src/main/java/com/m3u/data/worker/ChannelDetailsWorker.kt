package com.m3u.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
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
        // Every eligible playlist is walked by this one worker rather than by
        // one worker each. Live channels carry no description; only films and
        // series do.
        val playlistUrls = playlistDao.getAll()
            .filter { playlist -> playlist.isVod || playlist.isSeries }
            .map { playlist -> playlist.url }
        if (playlistUrls.isEmpty()) return Result.success()

        var fetched = 0
        try {
            for (playlistUrl in playlistUrls) {
                while (true) {
                    if (playerManager.isPlaying.value) {
                        // Someone is watching. Retry rather than fail:
                        // WorkManager backs off and returns once the box is
                        // idle again.
                        timber.d("playback in progress, yielding the connection")
                        return Result.retry()
                    }
                    val pending = channelDao.getWithoutDetails(playlistUrl, BATCH_SIZE)
                    if (pending.isEmpty()) break
                    for (channel in pending) {
                        if (isStopped) return Result.retry()
                        if (playerManager.isPlaying.value) return Result.retry()
                        channelDetailsRepository.fetchIfMissing(channel)
                        fetched++
                        delay(REQUEST_INTERVAL_MILLIS)
                    }
                }
            }
            timber.d("catalogue complete, $fetched fetched this run")
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            timber.w(e, "sweep interrupted after $fetched")
            return Result.retry()
        }
    }

    companion object {
        private const val BATCH_SIZE = 50

        /**
         * Deliberately unhurried. Descriptions are worth nothing on a schedule,
         * and a burst of thousands of requests is what a panel reads as abuse.
         *
         * The pause only holds if a single sweep is running — measured: one
         * worker per playlist doubled the rate to some 247 titles a minute,
         * which is why [WORK_NAME] is global rather than per playlist.
         */
        private const val REQUEST_INTERVAL_MILLIS = 250L

        private const val WORK_NAME = "channel-details-sweep"

        /**
         * How often the sweep comes back for another go.
         *
         * Periodic rather than a single long run, for two reasons. The platform
         * stops any worker after about ten minutes, so one run was never going
         * to walk a catalogue this size anyway. And playback interrupts the
         * sweep on purpose — without something bringing it back, a single
         * evening of watching would leave it stopped for good.
         */
        private val SWEEP_INTERVAL = 30L to TimeUnit.MINUTES

        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                // Keep, not update: re-enqueueing on every app start must not
                // reset the schedule of a sweep already making its way through.
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ChannelDetailsWorker>(
                    SWEEP_INTERVAL.first,
                    SWEEP_INTERVAL.second,
                )
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.MINUTES)
                    .build()
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
