package com.m3u.extension.transport.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidExtensionDiscoveryPolicyTest {
    @Test
    fun `shared uid is rejected without relying on package visibility`() {
        assertEquals(
            "Extension package must have its own Android UID",
            extensionIdentityIncompatibilityReason(
                processIdentityIncompatible = false,
                usesSharedUserId = true,
                hasDirectNetworkAccess = false,
            ),
        )
    }

    @Test
    fun `direct uid network access is rejected`() {
        assertEquals(
            "Extension package must use the host network broker",
            extensionIdentityIncompatibilityReason(
                processIdentityIncompatible = false,
                usesSharedUserId = false,
                hasDirectNetworkAccess = true,
            ),
        )
    }

    @Test
    fun `dedicated broker-only process is accepted`() {
        assertNull(
            extensionIdentityIncompatibilityReason(
                processIdentityIncompatible = false,
                usesSharedUserId = false,
                hasDirectNetworkAccess = false,
            )
        )
    }
}
