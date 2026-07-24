package com.m3u.data.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionContributionWorkerTest {
    @Test
    fun failureRetriesBeforeAttemptLimitAndThenTerminates() {
        assertEquals(
            ListenableWorker.Result.retry(),
            extensionContributionFailureResult(runAttemptCount = 0),
        )
        assertEquals(
            ListenableWorker.Result.retry(),
            extensionContributionFailureResult(runAttemptCount = 2),
        )
        assertEquals(
            ListenableWorker.Result.failure(),
            extensionContributionFailureResult(runAttemptCount = 3),
        )
    }
}
