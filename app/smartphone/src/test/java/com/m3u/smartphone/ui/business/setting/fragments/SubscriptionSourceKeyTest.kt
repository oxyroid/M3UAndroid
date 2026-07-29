package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.data.database.model.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SubscriptionSourceKeyTest {
    @Test
    fun `ordinary source keys round trip without depending on display text`() {
        listOf(DataSource.M3U, DataSource.EPG, DataSource.Xtream).forEach { source ->
            assertEquals(
                source,
                ordinarySubscriptionSourceOrNull(source.subscriptionSelectionKey()),
            )
        }
    }

    @Test
    fun `provider keys are namespaced and never decode as ordinary sources`() {
        val key = providerSourceSelectionKey(
            providerId = "com.example.provider",
            providerKind = "live",
        )

        assertEquals("provider:com.example.provider:live", key)
        assertNull(ordinarySubscriptionSourceOrNull(key))
    }
}
