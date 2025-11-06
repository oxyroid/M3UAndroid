package com.m3u.data.security

import android.content.Context
import com.m3u.core.architecture.preferences.PreferencesKeys
import com.m3u.core.architecture.preferences.Settings
import com.m3u.data.repository.usbkey.HealthStatus
import com.m3u.data.repository.usbkey.USBKeyState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhancement #9: Encryption Status Dashboard
 * Calculates metrics and statistics for the encryption system
 */
@Singleton
class EncryptionMetricsCalculator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings
) {
    private val timber = Timber.tag("EncryptionMetricsCalculator")

    companion object {
        private const val DATABASE_NAME = "m3u-database"
        private const val ENCRYPTION_ALGORITHM = "AES-256-CBC (SQLCipher 4.5.4)"
        private const val VERIFICATION_WARNING_THRESHOLD_HOURS = 24
    }

    /**
     * Calculate database file size in bytes
     * @return Database size in bytes, or null if database doesn't exist
     */
    fun calculateDatabaseSize(): Long? {
        return try {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (dbFile.exists()) {
                val sizeBytes = dbFile.length()
                timber.d("Database size: $sizeBytes bytes (${formatBytes(sizeBytes)})")
                sizeBytes
            } else {
                timber.d("Database file does not exist yet")
                null
            }
        } catch (e: Exception) {
            timber.e(e, "Failed to calculate database size")
            null
        }
    }

    /**
     * Get encryption algorithm description
     * @return Human-readable encryption algorithm name
     */
    fun getEncryptionAlgorithm(): String {
        return ENCRYPTION_ALGORITHM
    }

    /**
     * Get relative time string for last verification
     * @return Human-readable relative time (e.g., "5 minutes ago", "2 hours ago")
     */
    suspend fun getLastVerifiedRelativeTime(): String? {
        return try {
            val timestamp = settings.data.first()[PreferencesKeys.USB_ENCRYPTION_LAST_VERIFIED] ?: 0L
            if (timestamp == 0L) {
                timber.d("No verification timestamp found")
                return null
            }

            val currentTime = System.currentTimeMillis()
            val diffMillis = currentTime - timestamp

            formatRelativeTime(diffMillis)
        } catch (e: Exception) {
            timber.e(e, "Failed to get last verified time")
            null
        }
    }

    /**
     * Calculate overall health status based on encryption state
     * @param state Current USB key state
     * @return Health status indicator
     */
    suspend fun calculateHealthStatus(state: USBKeyState): HealthStatus {
        return try {
            when {
                // Encryption disabled
                !state.isEncryptionEnabled -> {
                    timber.d("Health: DISABLED (encryption not enabled)")
                    HealthStatus.DISABLED
                }

                // Key verification failed
                state.verificationError != null -> {
                    timber.d("Health: CRITICAL (verification error: ${state.verificationError})")
                    HealthStatus.CRITICAL
                }

                // USB disconnected or app locked
                !state.isConnected || state.isLocked -> {
                    timber.d("Health: WARNING (USB disconnected or app locked)")
                    HealthStatus.WARNING
                }

                // Check verification timestamp
                else -> {
                    val lastVerified = state.lastVerificationTime
                    if (lastVerified == null) {
                        timber.d("Health: WARNING (never verified)")
                        HealthStatus.WARNING
                    } else {
                        val hoursSinceVerification = TimeUnit.MILLISECONDS.toHours(
                            System.currentTimeMillis() - lastVerified
                        )
                        if (hoursSinceVerification > VERIFICATION_WARNING_THRESHOLD_HOURS) {
                            timber.d("Health: WARNING (last verified $hoursSinceVerification hours ago)")
                            HealthStatus.WARNING
                        } else {
                            timber.d("Health: HEALTHY (all checks passed)")
                            HealthStatus.HEALTHY
                        }
                    }
                }
            }
        } catch (e: Exception) {
            timber.e(e, "Failed to calculate health status")
            HealthStatus.WARNING
        }
    }

    /**
     * Format bytes to human-readable string
     * @param bytes Size in bytes
     * @return Formatted string (e.g., "2.5 MB")
     */
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.2f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /**
     * Format relative time from milliseconds difference
     * @param diffMillis Time difference in milliseconds
     * @return Human-readable relative time string
     */
    private fun formatRelativeTime(diffMillis: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(diffMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffMillis)

        return when {
            seconds < 60 -> "Just now"
            minutes < 2 -> "1 minute ago"
            minutes < 60 -> "$minutes minutes ago"
            hours < 2 -> "1 hour ago"
            hours < 24 -> "$hours hours ago"
            days < 2 -> "1 day ago"
            days < 7 -> "$days days ago"
            days < 30 -> "${days / 7} weeks ago"
            else -> {
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                dateFormat.format(Date(System.currentTimeMillis() - diffMillis))
            }
        }
    }

    /**
     * Get estimated database encryption time based on size
     * @param databaseSizeBytes Database size in bytes
     * @return Estimated time in seconds
     */
    fun estimateEncryptionTime(databaseSizeBytes: Long): Long {
        // Rough estimate: ~1 second per MB on average Android TV hardware
        val sizeMb = databaseSizeBytes / (1024.0 * 1024.0)
        return maxOf(5, (sizeMb * 1.0).toLong()) // Minimum 5 seconds
    }
}
