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
        val original = InstalledExtensionService(PACKAGE, SERVICE, "certificate-a")
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
        val service = InstalledExtensionService(PACKAGE, SERVICE, "certificate-a")
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

    private companion object {
        const val PACKAGE = "com.example.extension"
        const val SERVICE = "com.example.extension.ExtensionService"
    }
}
