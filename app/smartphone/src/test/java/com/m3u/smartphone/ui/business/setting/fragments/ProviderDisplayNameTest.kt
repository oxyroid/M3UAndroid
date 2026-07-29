package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.data.repository.provider.DiscoveredSubscriptionProvider
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.SubscriptionProviderExecutionKind
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import com.m3u.smartphone.ui.business.configuration.providerDisplayName
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderDisplayNameTest {
    @Test
    fun `current provider variant name is preferred`() {
        assertEquals(
            "Cinema Cloud",
            providerDisplayName(
                account = ACCOUNT,
                discoveryState = ProviderDiscoveryState.Ready(
                    providers = listOf(
                        DiscoveredSubscriptionProvider(
                            descriptor = SubscriptionProviderDescriptor(
                                providerId = PROVIDER_ID,
                                displayName = "Cinema",
                                variants = listOf(
                                    SubscriptionProviderVariant(
                                        kind = PROVIDER_KIND,
                                        displayName = "Cinema Cloud",
                                    )
                                ),
                            ),
                            executionKind = SubscriptionProviderExecutionKind.EXTERNAL,
                        )
                    ),
                ),
            ),
        )
    }

    @Test
    fun `server name is a safe fallback when discovery is unavailable`() {
        assertEquals(
            "Living Room Server",
            providerDisplayName(
                account = ACCOUNT.copy(
                    serverName = "\u202ELiving Room Server\u202C",
                ),
                discoveryState = ProviderDiscoveryState.Failed(failureCount = 1),
            ),
        )
    }

    private companion object {
        val PROVIDER_ID = ExtensionId("example.cinema")
        val PROVIDER_KIND = ProviderKind("cloud")
        val ACCOUNT = ProviderAccountSummary(
            playlistTitle = "Living room",
            playlistUrl = "provider://account",
            providerId = PROVIDER_ID,
            providerKind = PROVIDER_KIND,
            baseUrl = "https://example.invalid",
            username = "viewer",
            serverName = "Living Room Server",
            requiresReauthentication = false,
        )
    }
}
