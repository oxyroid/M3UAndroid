package com.m3u.extension.transport.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `bound identity accepts the unchanged authorized service`() {
        val expected = installedService()

        assertTrue(
            matchesBoundServiceIdentity(
                expected = expected,
                connectedPackageName = expected.packageName,
                connectedServiceName = expected.serviceName,
                resolved = expected,
            )
        )
    }

    @Test
    fun `bound identity rejects a package replaced with another certificate`() {
        val expected = installedService()

        assertFalse(
            matchesBoundServiceIdentity(
                expected = expected,
                connectedPackageName = expected.packageName,
                connectedServiceName = expected.serviceName,
                resolved = expected.copy(certificateSha256 = "attacker-certificate"),
            )
        )
    }

    @Test
    fun `bound identity rejects a service whose uid changed after authorization`() {
        val expected = installedService()

        assertFalse(
            matchesBoundServiceIdentity(
                expected = expected,
                connectedPackageName = expected.packageName,
                connectedServiceName = expected.serviceName,
                resolved = expected.copy(uid = expected.uid + 1),
            )
        )
    }

    @Test
    fun `bound identity rejects a different connected component`() {
        val expected = installedService()

        assertFalse(
            matchesBoundServiceIdentity(
                expected = expected,
                connectedPackageName = expected.packageName,
                connectedServiceName = "attacker.Service",
                resolved = expected,
            )
        )
    }

    @Test
    fun `bound identity rejects a service that became incompatible`() {
        val expected = installedService()

        assertFalse(
            matchesBoundServiceIdentity(
                expected = expected,
                connectedPackageName = expected.packageName,
                connectedServiceName = expected.serviceName,
                resolved = expected.copy(
                    incompatibilityReason = "Extension package must use the host network broker"
                ),
            )
        )
    }

    @Test
    fun `bound identity mismatch is classified as incompatible`() {
        val expected = installedService()

        assertFailsWith<ExtensionTransportIncompatibleException> {
            requireCompatibleBoundServiceIdentity(
                expected = expected,
                connectedPackageName = expected.packageName,
                connectedServiceName = expected.serviceName,
                resolved = null,
            )
        }
    }

    private fun installedService() = InstalledExtensionService(
        packageName = "com.example.extension",
        serviceName = "com.example.extension.ExtensionService",
        certificateSha256 = "authorized-certificate",
        uid = 12_345,
    )
}
