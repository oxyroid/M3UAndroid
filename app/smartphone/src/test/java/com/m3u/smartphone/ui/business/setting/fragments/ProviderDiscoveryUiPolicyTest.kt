package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.business.setting.ProviderDiscoveryState
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderDiscoveryUiPolicyTest {
    @Test
    fun `ordinary source exposes loading and recoverable discovery failures`() {
        assertEquals(
            ProviderDiscoveryNotice.LOADING,
            providerDiscoveryNotice(
                state = ProviderDiscoveryState.Loading,
                providerSelected = false,
            ),
        )
        assertEquals(
            ProviderDiscoveryNotice.EMPTY,
            providerDiscoveryNotice(
                state = ProviderDiscoveryState.Empty,
                providerSelected = false,
            ),
        )
        assertEquals(
            ProviderDiscoveryNotice.FAILED,
            providerDiscoveryNotice(
                state = ProviderDiscoveryState.Failed(failureCount = 1),
                providerSelected = false,
            ),
        )
    }

    @Test
    fun `provider form owns its discovery notice when selected`() {
        listOf(
            ProviderDiscoveryState.Loading,
            ProviderDiscoveryState.Empty,
            ProviderDiscoveryState.Failed(failureCount = 1),
        ).forEach { state ->
            assertEquals(
                ProviderDiscoveryNotice.NONE,
                providerDiscoveryNotice(
                    state = state,
                    providerSelected = true,
                ),
            )
        }
    }
}
