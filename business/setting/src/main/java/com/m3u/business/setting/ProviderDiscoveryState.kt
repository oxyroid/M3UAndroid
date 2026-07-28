package com.m3u.business.setting

import com.m3u.data.repository.provider.DiscoveredSubscriptionProvider
import com.m3u.data.repository.provider.SubscriptionProviderExecutionKind
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.subscription.ProviderKind

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

data class ProviderSubscriptionSource(
    val providerId: ExtensionId,
    val providerKind: ProviderKind,
    val providerDisplayName: String,
    val displayName: String,
    val executionKind: SubscriptionProviderExecutionKind,
)

fun ProviderDiscoveryState.subscriptionSources(): List<ProviderSubscriptionSource> =
    (this as? ProviderDiscoveryState.Ready)
        ?.providers
        .orEmpty()
        .flatMap { provider ->
            provider.descriptor.variants
                .filter { variant -> variant.userSelectable }
                .map { variant ->
                    ProviderSubscriptionSource(
                        providerId = provider.descriptor.providerId,
                        providerKind = variant.kind,
                        providerDisplayName = provider.descriptor.displayName,
                        displayName = variant.displayName,
                        executionKind = provider.executionKind,
                    )
                }
        }

fun ProviderDiscoveryState.supports(
    form: ProviderSubscriptionForm?,
): Boolean {
    if (form == null) return false
    return (this as? ProviderDiscoveryState.Ready)
        ?.providers
        .orEmpty()
        .any { provider ->
            provider.descriptor.providerId == form.providerId &&
                provider.descriptor.variants.any { variant ->
                    variant.kind == form.providerKind &&
                        (variant.userSelectable || form.reauthenticationPlaylistUrl != null)
                }
        }
}

internal fun List<DiscoveredSubscriptionProvider>.reconcileSubscriptionForm(
    current: ProviderSubscriptionForm?,
): ProviderSubscriptionForm? {
    if (current == null) {
        val (descriptor, kind) = firstNotNullOfOrNull { provider ->
            provider.descriptor.variants
                .firstOrNull { variant -> variant.userSelectable }
                ?.let { variant -> provider.descriptor to variant.kind }
        } ?: return null
        return ProviderSubscriptionForm.create(
            descriptor = descriptor,
            providerKind = kind,
        )
    }

    val descriptor = firstOrNull { provider ->
        provider.descriptor.providerId == current.providerId
    }?.descriptor ?: return current
    if (descriptor.variants.none { variant -> variant.kind == current.providerKind }) {
        return current
    }

    val currentDefinitions = current.fields.map(ProviderSubscriptionFormField::definition)
    return if (
        current.schemaVersion != descriptor.settingsSchema?.version ||
        currentDefinitions != descriptor.settingsSchema?.fields.orEmpty()
    ) {
        current.updateDescriptor(descriptor)
    } else {
        current
    }
}
