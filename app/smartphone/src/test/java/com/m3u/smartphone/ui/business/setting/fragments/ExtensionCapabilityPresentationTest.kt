package com.m3u.smartphone.ui.business.setting.fragments

import com.m3u.extension.api.ExtensionCapabilityIds
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExtensionCapabilityPresentationTest {
    @Test
    fun `every standard capability has a host name`() {
        ExtensionCapabilityIds.All.forEach { capability ->
            assertNotNull(
                extensionCapabilityNameResource(capability.id),
                "Missing host name for ${capability.id}",
            )
        }
    }

    @Test
    fun `unknown optional capability keeps the wire id fallback`() {
        assertNull(extensionCapabilityNameResource("vendor.future.optional"))
    }
}
