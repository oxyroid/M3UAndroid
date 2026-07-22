package com.m3u.extension.transport.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionTrustStoreTest {
    @Test
    fun changedCertificateDoesNotInheritTrust() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE).edit().clear().commit()
        val store = ExtensionTrustStore(context)
        val original = InstalledExtensionService(PACKAGE, SERVICE, "certificate-a", uid = 10_001)
        val replaced = original.copy(certificateSha256 = "certificate-b")

        store.trust(
            service = original,
            extensionId = "com.example.extension",
            capabilities = setOf("network"),
            displayName = "Example",
            version = "1.0.0",
            developer = "Example Developer",
        )

        assertTrue(store.isTrusted(original))
        assertTrue(store.hasPinnedCertificate(replaced))
        assertFalse(store.isTrusted(replaced))
    }

    @Test
    fun explicitGrantUpdateCanPreserveDisabledState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE).edit().clear().commit()
        val store = ExtensionTrustStore(context)
        val service = InstalledExtensionService(PACKAGE, SERVICE, "certificate-a", uid = 10_001)
        store.trust(
            service = service,
            extensionId = "com.example.extension",
            capabilities = setOf("network"),
            displayName = "Example",
            version = "1.0.0",
            developer = null,
        )
        store.setEnabled(service, false)

        store.trust(
            service = service,
            extensionId = "com.example.extension",
            capabilities = setOf("network", "settings.contribute"),
            displayName = "Example",
            version = "1.1.0",
            developer = null,
        )
        store.setEnabled(service, false)

        assertTrue(store.isTrusted(service))
        assertFalse(store.isEnabled(service))
        assertEquals(
            setOf("network", "settings.contribute"),
            store.grantedCapabilities("com.example.extension"),
        )
    }

    @Test
    fun duplicateExtensionIdsNeverShareCapabilities() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE).edit().clear().commit()
        val store = ExtensionTrustStore(context)
        val first = InstalledExtensionService(PACKAGE, SERVICE, "certificate-a", uid = 10_001)
        val second = InstalledExtensionService(
            packageName = "com.example.other",
            serviceName = "com.example.other.ExtensionService",
            certificateSha256 = "certificate-b",
            uid = 10_002,
        )
        store.trust(
            service = first,
            extensionId = EXTENSION_ID,
            capabilities = setOf("network"),
            displayName = "First",
            version = "1.0.0",
            developer = null,
        )
        store.trust(
            service = second,
            extensionId = EXTENSION_ID,
            capabilities = setOf("settings.contribute"),
            displayName = "Second",
            version = "1.0.0",
            developer = null,
        )

        assertEquals(setOf("network"), store.grantedCapabilities(first))
        assertEquals(setOf("settings.contribute"), store.grantedCapabilities(second))
        assertTrue(store.grantedCapabilities(EXTENSION_ID).isEmpty())
        assertTrue(store.isExtensionIdClaimedByAnotherService(first, EXTENSION_ID))
        assertTrue(store.isExtensionIdClaimedByAnotherService(second, EXTENSION_ID))
        assertFalse(store.isSoleStoredOwner(first.packageName, first.serviceName, EXTENSION_ID))
        assertFalse(store.isSoleStoredOwner(second.packageName, second.serviceName, EXTENSION_ID))

        store.revoke(first.packageName, first.serviceName)

        assertTrue(store.isSoleStoredOwner(second.packageName, second.serviceName, EXTENSION_ID))
    }

    @Test
    fun trustedServicesExposeAndForgetAServiceThatIsNoLongerInstalled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("extension-trust", Context.MODE_PRIVATE).edit().clear().commit()
        val store = ExtensionTrustStore(context)
        val service = InstalledExtensionService(PACKAGE, SERVICE, "certificate-a", uid = 10_001)
        store.trust(
            service = service,
            extensionId = EXTENSION_ID,
            capabilities = setOf("network"),
            displayName = "Example",
            version = "1.0.0",
            developer = "Example Developer",
        )

        val record = store.trustedServices().single()
        assertEquals(service.packageName, record.packageName)
        assertEquals(service.serviceName, record.serviceName)
        assertEquals(service.certificateSha256, record.certificateSha256)
        assertEquals(EXTENSION_ID, record.extensionId)
        assertEquals(setOf("network"), record.capabilities)

        store.revoke(service.packageName, service.serviceName)

        assertTrue(store.trustedServices().isEmpty())
        assertFalse(store.isExtensionIdClaimedByAnotherService(service, EXTENSION_ID))
    }

    private companion object {
        const val PACKAGE = "com.example.extension"
        const val SERVICE = "com.example.extension.ExtensionService"
        const val EXTENSION_ID = "com.example.extension"
    }
}
