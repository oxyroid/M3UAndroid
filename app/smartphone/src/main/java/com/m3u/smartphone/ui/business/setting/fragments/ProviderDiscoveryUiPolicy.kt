package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.business.setting.ProviderDiscoveryState

internal enum class ProviderDiscoveryNotice {
    NONE,
    LOADING,
    EMPTY,
    FAILED,
}

/**
 * Keeps provider discovery recoverable even while an ordinary source is selected.
 *
 * A selected provider renders its more specific availability state inside the form.
 */
internal fun providerDiscoveryNotice(
    state: ProviderDiscoveryState,
    providerSelected: Boolean,
): ProviderDiscoveryNotice {
    if (providerSelected) return ProviderDiscoveryNotice.NONE
    return when (state) {
        ProviderDiscoveryState.Loading -> ProviderDiscoveryNotice.LOADING
        ProviderDiscoveryState.Empty -> ProviderDiscoveryNotice.EMPTY
        is ProviderDiscoveryState.Failed -> ProviderDiscoveryNotice.FAILED
        is ProviderDiscoveryState.Ready -> ProviderDiscoveryNotice.NONE
    }
}
