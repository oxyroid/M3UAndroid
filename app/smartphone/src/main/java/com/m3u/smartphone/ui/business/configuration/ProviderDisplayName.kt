package com.m3u.smartphone.ui.business.configuration

import com.m3u.business.setting.ProviderDiscoveryState
import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.smartphone.ui.material.ktx.safeDisplayText

internal fun providerDisplayName(
    account: ProviderAccountSummary,
    discoveryState: ProviderDiscoveryState?,
): String {
    val descriptor = (discoveryState as? ProviderDiscoveryState.Ready)
        ?.providers
        .orEmpty()
        .firstOrNull { provider ->
            provider.descriptor.providerId == account.providerId
        }
        ?.descriptor
    val variantName = descriptor
        ?.variants
        ?.firstOrNull { variant -> variant.kind == account.providerKind }
        ?.displayName

    return sequenceOf(
        variantName,
        descriptor?.displayName,
        account.serverName,
        account.providerKind.value,
    )
        .filterNotNull()
        .map { value -> value.safeDisplayText() }
        .firstOrNull(String::isNotBlank)
        .orEmpty()
}
