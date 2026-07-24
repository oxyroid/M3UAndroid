package com.m3u.data.worker

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.workDataOf
import com.m3u.extension.api.subscription.SubscriptionRefreshReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderRefreshWorkerInputTest {
    @Test
    fun userRefreshRetainsManualReasonAcrossWorkManagerInput() {
        val input = providerRefreshInputData(
            playlistUrl = "m3u-provider://account/account/live",
            reason = SubscriptionRefreshReason.Manual,
        )

        assertEquals(
            SubscriptionRefreshReason.Manual,
            input.providerRefreshReasonOrNull(),
        )
    }

    @Test
    fun backgroundRefreshRetainsBackgroundReasonAcrossWorkManagerInput() {
        val input = providerRefreshInputData(
            playlistUrl = "m3u-provider://account/account/live",
            reason = SubscriptionRefreshReason.Background,
        )

        assertEquals(
            SubscriptionRefreshReason.Background,
            input.providerRefreshReasonOrNull(),
        )
    }

    @Test
    fun missingOrInvalidReasonIsRejected() {
        assertNull(workDataOf().providerRefreshReasonOrNull())
        assertNull(
            workDataOf(
                ProviderRefreshWorker.INPUT_REFRESH_REASON to "INVALID REASON"
            ).providerRefreshReasonOrNull()
        )
    }
}
