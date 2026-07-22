package com.m3u.business.setting

import com.m3u.data.repository.provider.DiscoveredSubscriptionProvider
import com.m3u.data.repository.provider.SubscriptionProviderExecutionKind
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor

sealed interface ProviderDiscoveryState {
    data object Loading : ProviderDiscoveryState

    data class Ready(
        val providers: List<DiscoveredSubscriptionProvider>,
    ) : ProviderDiscoveryState {
        init {
            require(providers.isNotEmpty())
        }
    }

    data object Empty : ProviderDiscoveryState

    data class Failed(
        val failureCount: Int?,
    ) : ProviderDiscoveryState
}

internal fun List<DiscoveredSubscriptionProvider>.toProviderDiscoveryState(): ProviderDiscoveryState =
    if (isEmpty()) ProviderDiscoveryState.Empty else ProviderDiscoveryState.Ready(this)

internal fun List<DiscoveredSubscriptionProvider>.singleBuiltInProviderFor(
    kind: ProviderKind,
): SubscriptionProviderDescriptor? = asSequence()
    .filter { provider ->
        provider.executionKind == SubscriptionProviderExecutionKind.BUILT_IN &&
            provider.descriptor.variants.any { variant -> variant.kind == kind }
    }
    .map(DiscoveredSubscriptionProvider::descriptor)
    .singleOrNull()
