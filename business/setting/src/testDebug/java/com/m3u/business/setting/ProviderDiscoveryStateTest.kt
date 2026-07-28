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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `subscription sources flatten every descriptor variant without provider branches`() {
        val builtIn = provider(
            id = "com.example.builtin",
            executionKind = SubscriptionProviderExecutionKind.BUILT_IN,
            variants = listOf(
                SubscriptionProviderVariant(ProviderKind("alpha"), "Alpha"),
                SubscriptionProviderVariant(ProviderKind("beta"), "Beta"),
                SubscriptionProviderVariant(
                    kind = ProviderKind("legacy"),
                    displayName = "Legacy",
                    userSelectable = false,
                ),
            ),
        )
        val external = provider(
            id = "com.example.external",
            executionKind = SubscriptionProviderExecutionKind.EXTERNAL,
            variants = listOf(
                SubscriptionProviderVariant(ProviderKind("custom"), "Custom"),
            ),
        )

        assertEquals(
            listOf(
                ProviderSubscriptionSource(
                    providerId = ExtensionId("com.example.builtin"),
                    providerKind = ProviderKind("alpha"),
                    providerDisplayName = "com.example.builtin",
                    displayName = "Alpha",
                    executionKind = SubscriptionProviderExecutionKind.BUILT_IN,
                ),
                ProviderSubscriptionSource(
                    providerId = ExtensionId("com.example.builtin"),
                    providerKind = ProviderKind("beta"),
                    providerDisplayName = "com.example.builtin",
                    displayName = "Beta",
                    executionKind = SubscriptionProviderExecutionKind.BUILT_IN,
                ),
                ProviderSubscriptionSource(
                    providerId = ExtensionId("com.example.external"),
                    providerKind = ProviderKind("custom"),
                    providerDisplayName = "com.example.external",
                    displayName = "Custom",
                    executionKind = SubscriptionProviderExecutionKind.EXTERNAL,
                ),
            ),
            ProviderDiscoveryState.Ready(listOf(builtIn, external)).subscriptionSources(),
        )
    }

    @Test
    fun `non-ready discovery states expose no selectable provider sources`() {
        assertEquals(emptyList(), ProviderDiscoveryState.Loading.subscriptionSources())
        assertEquals(emptyList(), ProviderDiscoveryState.Empty.subscriptionSources())
        assertEquals(emptyList(), ProviderDiscoveryState.Failed(1).subscriptionSources())
    }

    @Test
    fun `availability requires the exact provider and kind in a ready catalog`() {
        val legacyKind = ProviderKind("legacy")
        val discovered = provider(
            id = "com.example.provider",
            executionKind = SubscriptionProviderExecutionKind.EXTERNAL,
            variants = listOf(
                SubscriptionProviderVariant(
                    kind = legacyKind,
                    displayName = "Legacy",
                    userSelectable = false,
                )
            ),
        )
        val form = ProviderSubscriptionForm.create(discovered.descriptor, legacyKind)
        val reauthenticationForm = form.copy(
            reauthenticationPlaylistUrl = "m3u-provider://account/legacy/live",
        )
        val ready = ProviderDiscoveryState.Ready(listOf(discovered))

        assertFalse(ready.supports(form))
        assertTrue(ready.supports(reauthenticationForm))
        assertFalse(ProviderDiscoveryState.Loading.supports(form))
        assertFalse(ProviderDiscoveryState.Empty.supports(form))
        assertFalse(ProviderDiscoveryState.Failed(1).supports(form))
        assertFalse(ready.supports(null))
    }

    @Test
    fun `default reconciliation only creates a selectable provider form`() {
        val hiddenKind = ProviderKind("hidden")
        val visibleKind = ProviderKind("visible")
        val mixed = provider(
            id = "com.example.mixed",
            executionKind = SubscriptionProviderExecutionKind.BUILT_IN,
            variants = listOf(
                SubscriptionProviderVariant(
                    kind = hiddenKind,
                    displayName = "Hidden",
                    userSelectable = false,
                ),
                SubscriptionProviderVariant(visibleKind, "Visible"),
            ),
        )
        val hiddenOnly = provider(
            id = "com.example.hidden",
            executionKind = SubscriptionProviderExecutionKind.BUILT_IN,
            variants = listOf(
                SubscriptionProviderVariant(
                    kind = hiddenKind,
                    displayName = "Hidden",
                    userSelectable = false,
                )
            ),
        )

        assertEquals(
            visibleKind,
            listOf(mixed).reconcileSubscriptionForm(current = null)?.providerKind,
        )
        assertNull(listOf(hiddenOnly).reconcileSubscriptionForm(current = null))
    }

    @Test
    fun `reconciliation keeps edited form when its provider is temporarily absent`() {
        val selected = provider(
            id = "com.example.selected",
            executionKind = SubscriptionProviderExecutionKind.EXTERNAL,
        )
        val current = ProviderSubscriptionForm.create(selected.descriptor, KIND)
            .update("base_url", "https://selected.example")
        val remaining = provider(
            id = "com.example.remaining",
            executionKind = SubscriptionProviderExecutionKind.BUILT_IN,
        )

        assertEquals(
            current,
            listOf(remaining).reconcileSubscriptionForm(current),
        )
        assertEquals(
            current,
            emptyList<DiscoveredSubscriptionProvider>().reconcileSubscriptionForm(current),
        )
    }

    @Test
    fun `reconciliation keeps edited form when its kind is temporarily absent`() {
        val selected = provider(
            id = "com.example.selected",
            executionKind = SubscriptionProviderExecutionKind.EXTERNAL,
        )
        val current = ProviderSubscriptionForm.create(selected.descriptor, KIND)
            .update("base_url", "https://selected.example")
        val changed = provider(
            id = "com.example.selected",
            executionKind = SubscriptionProviderExecutionKind.EXTERNAL,
            variants = listOf(
                SubscriptionProviderVariant(ProviderKind("other"), "Other")
            ),
        )

        assertEquals(
            current,
            listOf(changed).reconcileSubscriptionForm(current),
        )
    }

    private fun provider(
        id: String,
        executionKind: SubscriptionProviderExecutionKind,
        variants: List<SubscriptionProviderVariant> = listOf(
            SubscriptionProviderVariant(KIND, "Example")
        ),
    ) = DiscoveredSubscriptionProvider(
        descriptor = SubscriptionProviderDescriptor(
            providerId = ExtensionId(id),
            displayName = id,
            variants = variants,
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
