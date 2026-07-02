package com.m3u.data.worker

import androidx.work.Constraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class SubscriptionWorkerTest {
    @Test
    fun m3uSubscriptionsDoNotRequireWorkManagerNetworkValidation() {
        val constraints = SubscriptionWorker.m3uConstraints()

        assertEquals(Constraints.NONE, constraints)
    }

    @Test
    fun m3uTimeoutFailureRetriesBeforeRetryLimit() {
        val error = IllegalStateException("Failed to fetch playlist: HTTP 999 timeout")

        assertTrue(SubscriptionWorker.shouldRetryM3uFailure(error, runAttemptCount = 0))
        assertTrue(SubscriptionWorker.shouldRetryM3uFailure(error, runAttemptCount = 1))
        assertFalse(SubscriptionWorker.shouldRetryM3uFailure(error, runAttemptCount = 2))
    }

    @Test
    fun m3uIoFailureRetriesBeforeRetryLimit() {
        val error = IOException("connection reset")

        assertTrue(SubscriptionWorker.shouldRetryM3uFailure(error, runAttemptCount = 0))
    }

    @Test
    fun m3uNonTransientHttpFailureDoesNotRetry() {
        val error = IllegalStateException("Failed to fetch playlist: HTTP 404 Not Found")

        assertFalse(SubscriptionWorker.shouldRetryM3uFailure(error, runAttemptCount = 0))
    }

    @Test
    fun m3uMissingLocalFileDoesNotRetry() {
        val error = FileNotFoundException("/sdcard/Download/FreeTV.m3u: open failed")

        assertFalse(SubscriptionWorker.shouldRetryM3uFailure(error, runAttemptCount = 0))
    }

    @Test
    fun m3uPermissionFailureDoesNotRetry() {
        val error = SecurityException("Permission denied")

        assertFalse(SubscriptionWorker.shouldRetryM3uFailure(error, runAttemptCount = 0))
    }
}
