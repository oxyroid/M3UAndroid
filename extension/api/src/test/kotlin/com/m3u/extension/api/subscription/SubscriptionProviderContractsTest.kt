package com.m3u.extension.api.subscription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SubscriptionProviderContractsTest {
    @Test
    fun `compatible provider exposes distinct user facing kinds`() {
        assertEquals("emby", EmbyCompatibleProviderKinds.Emby.value)
        assertEquals("jellyfin", EmbyCompatibleProviderKinds.Jellyfin.value)
        assertEquals("auto", EmbyCompatibleProviderKinds.Auto.value)
    }

    @Test
    fun `provider kind rejects non portable identifier`() {
        assertFailsWith<IllegalArgumentException> { ProviderKind("Emby Server") }
    }

    @Test
    fun `provider variants are selectable unless declared as compatibility-only`() {
        assertEquals(
            true,
            SubscriptionProviderVariant(
                kind = EmbyCompatibleProviderKinds.Emby,
                displayName = "Emby",
            ).userSelectable,
        )
        assertEquals(
            false,
            SubscriptionProviderVariant(
                kind = EmbyCompatibleProviderKinds.Auto,
                displayName = "Automatic",
                userSelectable = false,
            ).userSelectable,
        )
    }
}
