package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.m3u.data.R
import com.m3u.data.repository.media.MediaRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@HiltWorker
class ProgrammeReminder @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val programmeRepository: ProgrammeRepository,
    private val notificationManager: NotificationManager,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(context, params) {
    private val programmeId = inputData.getInt(PROGRAMME_ID, -1)
    private val notificationId: Int by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ATOMIC_NOTIFICATION_ID.incrementAndGet()
    }

    override suspend fun doWork(): Result {
        createChannel()
        if (programmeId == -1) {
            return Result.failure()
        }
        val programme = programmeRepository.getById(programmeId) ?: return Result.failure()
        val drawable = mediaRepository.loadDrawable(programme.icon.orEmpty())
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(programme.title)
            .setContentText(programme.description)
            .setSmallIcon(R.drawable.baseline_notifications_none_24)
        if (drawable != null) {
            builder.setLargeIcon(drawable.toBitmapOrNull())
        }
        notificationManager.notify(
            notificationId,
            builder.build()
        )
        return Result.success()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            NOTIFICATION_NAME,
            NotificationManager.IMPORTANCE_HIGH
        )
        channel.description = "Receive programme notifications"
        channel.setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build()
        )
        notificationManager.createNotificationChannel(channel)
    }


    companion object {
        private const val CHANNEL_ID = "reminder_channel"
        private const val NOTIFICATION_NAME = "programme_reminder"
        private const val PROGRAMME_ID = "programme_id"
        fun readProgrammeId(tags: Collection<String>): Int? = tags.find {
            it.startsWith("id=")
        }
            ?.drop(3)
            ?.toIntOrNull()

        const val TAG = CHANNEL_ID
        private val ATOMIC_NOTIFICATION_ID = AtomicInteger()

        operator fun invoke(
            workManager: WorkManager,
            programmeId: Int,
            programmeStart: Long
        ) {
            val now = Clock.System.now().toEpochMilliseconds()
            if (now > programmeStart) return
            val data = workDataOf(
                PROGRAMME_ID to programmeId
            )
            val request = OneTimeWorkRequestBuilder<ProgrammeReminder>()
                .addTag(TAG)
                .addTag("id=$programmeId")
                .setInputData(data)
                .setInitialDelay(programmeStart - now, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueue(request)
        }
    }
}