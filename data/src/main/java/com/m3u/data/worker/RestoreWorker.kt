package com.m3u.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.m3u.data.R
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

@HiltWorker
class RestoreWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
    private val notificationManager: NotificationManager,
) : CoroutineWorker(context, params) {
    private val timber = Timber.tag("RestoreWorker")
    private val uri = inputData.getString(INPUT_URI)?.let { Uri.parse(it) }

    override suspend fun doWork(): Result {
        createChannel()
        uri ?: return Result.failure()

        timber.d("=== RESTORE WORKER STARTING ===")
        timber.d("Restore from: $uri")

        try {
            // ENTERPRISE FEATURE: Create safety backup before restore
            timber.d("Creating pre-restore safety backup...")
            createPreRestoreBackup()

            // Perform the actual restore
            timber.d("Performing restore...")
            playlistRepository.restoreOrThrow(uri)

            timber.d("✓ Restore completed successfully")
            return Result.success()
        } catch (e: SecurityException) {
            // Checksum verification failed - corrupted backup
            timber.e(e, "✗ Restore failed: Backup file is corrupted")
            timber.e("Safety backup was created but not used (original data preserved)")
            return Result.failure()
        } catch (e: Exception) {
            timber.e(e, "✗ Restore failed with exception")
            timber.w("Safety backup is available if data was lost")
            return Result.failure()
        }
    }

    /**
     * ENTERPRISE FEATURE: Pre-Restore Safety Backup
     *
     * Creates an automatic backup of current data before restore operation.
     * This provides a safety net in case restore fails or corrupt data is restored.
     *
     * Backup location: /data/data/com.m3u.tv/files/restore_backups/
     * Naming: pre_restore_backup_<timestamp>.m3u
     */
    private suspend fun createPreRestoreBackup() {
        try {
            val backupDir = File(context.filesDir, "restore_backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
                timber.d("Created restore backup directory: ${backupDir.absolutePath}")
            }

            // Create backup with timestamp
            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupDir, "pre_restore_backup_$timestamp.m3u")
            val backupUri = Uri.fromFile(backupFile)

            timber.d("Creating safety backup: ${backupFile.name}")
            playlistRepository.backupOrThrow(backupUri)

            timber.d("✓ Pre-restore safety backup created: ${backupFile.absolutePath}")
            timber.d("  Size: ${backupFile.length()} bytes")

            // Keep only last 3 pre-restore backups to save space
            pruneOldBackups(backupDir, keepCount = 3)

        } catch (e: Exception) {
            timber.w(e, "⚠ Could not create pre-restore backup (non-critical)")
            // Don't fail restore if safety backup fails - proceed with caution
        }
    }

    /**
     * Keeps only the N most recent backups, deletes older ones.
     */
    private fun pruneOldBackups(backupDir: File, keepCount: Int) {
        try {
            val backupFiles = backupDir.listFiles()
                ?.filter { it.name.startsWith("pre_restore_backup_") }
                ?.sortedByDescending { it.lastModified() }
                ?: return

            if (backupFiles.size > keepCount) {
                val filesToDelete = backupFiles.drop(keepCount)
                timber.d("Pruning ${filesToDelete.size} old pre-restore backups")

                filesToDelete.forEach { file ->
                    val deleted = file.delete()
                    if (deleted) {
                        timber.d("  Deleted: ${file.name}")
                        // Also delete associated checksum file
                        val checksumFile = File(file.absolutePath + ".checksum")
                        if (checksumFile.exists()) {
                            checksumFile.delete()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            timber.w(e, "Could not prune old backups")
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return ForegroundInfo(NOTIFICATION_ID, createNotification())
    }

    private fun createNotification(): Notification {
        return Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_file_download_24)
            .setContentTitle("Backing up")
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, NOTIFICATION_NAME, NotificationManager.IMPORTANCE_LOW
        )
        channel.description = "display subscribe task progress"
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "subscribe_channel"
        private const val NOTIFICATION_NAME = "restore task"
        private const val NOTIFICATION_ID = 1226
        const val TAG = "restore"
        const val INPUT_URI = "uri"
    }
}