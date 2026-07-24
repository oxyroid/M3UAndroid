package com.m3u.business.setting

import com.m3u.data.repository.provider.DiscoveredSubscriptionProvider
import com.m3u.data.repository.provider.SubscriptionProviderExecutionKind
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProviderDiscoveryStateTest {
    @Test
    fun `empty and non-empty catalogs map to explicit states`() {
        assertIs<ProviderDiscoveryState.Empty>(emptyList<DiscoveredSubscriptionProvider>()
            .toProviderDiscoveryState())

        val providers = listOf(provider("com.example.provider", SubscriptionProviderExecutionKind.BUILT_IN))
        val ready = assertIs<ProviderDiscoveryState.Ready>(providers.toProviderDiscoveryState())

        assertEquals(providers, ready.providers)
    }

    @Test
    fun `legacy shortcut never selects an external provider with the same kind`() {
        val external = provider(
            id = "com.example.external",
            executionKind = SubscriptionProviderExecutionKind.EXTERNAL,
        )

        assertNull(listOf(external).singleBuiltInProviderFor(KIND))

        val builtIn = provider(
            id = "com.example.builtin",
            executionKind = SubscriptionProviderExecutionKind.BUILT_IN,
        )
        assertEquals(
            builtIn.descriptor,
            listOf(external, builtIn).singleBuiltInProviderFor(KIND),
        )
    }

    @Test
    fun `legacy shortcut fails closed when more than one built-in matches`() {
        assertNull(
            listOf(
                provider("com.example.first", SubscriptionProviderExecutionKind.BUILT_IN),
                provider("com.example.second", SubscriptionProviderExecutionKind.BUILT_IN),
            ).singleBuiltInProviderFor(KIND)
        )
    }

    private fun provider(
        id: String,
        executionKind: SubscriptionProviderExecutionKind,
    ) = DiscoveredSubscriptionProvider(
        descriptor = SubscriptionProviderDescriptor(
            providerId = ExtensionId(id),
            displayName = id,
            variants = listOf(SubscriptionProviderVariant(KIND, "Example")),
            settingsSchema = ExtensionSettingSchema(
                version = 1,
                fields = listOf(
                    ExtensionSettingField(
                        key = "base_url",
                        label = "Server",
                        type = ExtensionSettingType.TEXT,
                        required = true,
                    )
                ),
            ),
        ),
        executionKind = executionKind,
    )

    private companion object {
        val KIND = ProviderKind("shared-kind")
    }
}
