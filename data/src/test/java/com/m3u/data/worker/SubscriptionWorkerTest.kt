package com.m3u.data.worker

import androidx.work.Constraints
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionWorkerTest {
    @Test
    fun m3uRemoteUrlDoesNotWaitForValidatedNetwork() {
        val constraints = SubscriptionWorker.M3U_CONSTRAINTS

        assertEquals(Constraints.NONE, constraints)
    }

    @Test
    fun m3uLocalFileDoesNotRequireNetwork() {
        val constraints = SubscriptionWorker.M3U_CONSTRAINTS

        assertEquals(Constraints.NONE, constraints)
    }

    @Test
    fun m3uContentUriDoesNotRequireNetwork() {
        val constraints = SubscriptionWorker.M3U_CONSTRAINTS

        assertEquals(Constraints.NONE, constraints)
    }
}
