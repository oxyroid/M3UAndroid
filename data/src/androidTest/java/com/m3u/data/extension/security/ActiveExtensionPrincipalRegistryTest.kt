package com.m3u.data.extension.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.model.ProviderAccount
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.transport.android.InstalledExtensionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActiveExtensionPrincipalRegistryTest {
    @Test
    fun installedServiceCreatesPrincipalWithFullIdentityAndUid() {
        val service = InstalledExtensionService(
            packageName = PACKAGE_NAME,
            serviceName = SERVICE_NAME,
            certificateSha256 = CERTIFICATE_SHA256,
            uid = UID,
        )

        assertEquals(
            ExtensionPrincipal(
                extensionId = EXTENSION_ID,
                packageName = PACKAGE_NAME,
                serviceName = SERVICE_NAME,
                certificateSha256 = CERTIFICATE_SHA256,
                uid = UID,
            ),
            service.toPrincipal(EXTENSION_ID),
        )
    }

    @Test
    fun principalOwnsOnlyAccountWithExactStoredIdentity() {
        val principal = principal()
        val account = account()

        assertTrue(principal.owns(account))
        assertFalse(principal.owns(account.copy(providerId = OTHER_EXTENSION_ID.value)))
        assertFalse(principal.owns(account.copy(ownerPackageName = OTHER_PACKAGE_NAME)))
        assertFalse(principal.owns(account.copy(ownerServiceName = OTHER_SERVICE_NAME)))
        assertFalse(principal.owns(account.copy(ownerCertificateSha256 = OTHER_CERTIFICATE_SHA256)))
        assertFalse(
            principal.owns(
                account.copy(
                    ownerPackageName = null,
                    ownerServiceName = null,
                    ownerCertificateSha256 = null,
                )
            )
        )
    }

    @Test
    fun activationIsIdempotentButRejectsAnyDifferentActiveIdentityOrUid() {
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        registry.activate(principal)

        assertEquals(principal, registry.active(EXTENSION_ID))
        assertTrue(registry.isActive(principal))
        listOf(
            principal.copy(packageName = OTHER_PACKAGE_NAME),
            principal.copy(serviceName = OTHER_SERVICE_NAME),
            principal.copy(certificateSha256 = OTHER_CERTIFICATE_SHA256),
            principal.copy(uid = OTHER_UID),
        ).forEach { conflicting ->
            assertFalse(registry.isActive(conflicting))
            assertThrows(IllegalStateException::class.java) {
                registry.activate(conflicting)
            }
            assertEquals(principal, registry.active(EXTENSION_ID))
        }
    }

    @Test
    fun deactivateRequiresMatchingPackageAndServiceAndReturnsExactPrincipal() {
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)

        assertNull(registry.deactivate(EXTENSION_ID, OTHER_PACKAGE_NAME, SERVICE_NAME))
        assertNull(registry.deactivate(EXTENSION_ID, PACKAGE_NAME, OTHER_SERVICE_NAME))
        assertTrue(registry.isActive(principal))
        assertEquals(
            principal,
            registry.deactivate(EXTENSION_ID, PACKAGE_NAME, SERVICE_NAME),
        )
        assertNull(registry.active(EXTENSION_ID))
        assertFalse(registry.isActive(principal))
        assertNull(registry.deactivate(EXTENSION_ID, PACKAGE_NAME, SERVICE_NAME))
    }

    @Test
    fun deactivateInvalidatesQueuedCommitAndAwaitPersistenceWaitsForStartedCommit() = runBlocking {
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        val startedLease = checkNotNull(registry.captureLease(EXTENSION_ID))
        val queuedLease = checkNotNull(registry.captureLease(EXTENSION_ID))
        val commitStarted = CompletableDeferred<Unit>()
        val releaseCommit = CompletableDeferred<Unit>()
        val startedCommit = async {
            registry.commit(startedLease) {
                commitStarted.complete(Unit)
                releaseCommit.await()
                "saved"
            }
        }

        commitStarted.await()
        assertEquals(
            principal,
            registry.deactivate(EXTENSION_ID, PACKAGE_NAME, SERVICE_NAME),
        )
        val awaitPersistence = async { registry.awaitPersistence(EXTENSION_ID) }
        val queuedCommit = async { runCatching { registry.commit(queuedLease) { "stale" } } }

        yield()
        assertFalse(awaitPersistence.isCompleted)
        releaseCommit.complete(Unit)

        assertEquals("saved", startedCommit.await())
        awaitPersistence.await()
        assertTrue(
            queuedCommit.await().exceptionOrNull() is InactiveExtensionPrincipalLeaseException
        )
    }

    @Test
    fun clearInvalidationRejectsOldLeaseAndAllowsOnlyPostClearGeneration() = runBlocking {
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        val staleLease = checkNotNull(registry.captureLease(EXTENSION_ID))

        registry.invalidateAndRun(EXTENSION_ID) {
            assertNull(registry.captureLease(EXTENSION_ID))
            assertFalse(registry.isActive(principal))
        }

        assertTrue(
            runCatching { registry.commit(staleLease) { Unit } }.exceptionOrNull() is
                InactiveExtensionPrincipalLeaseException
        )
        val currentLease = checkNotNull(registry.captureLease(EXTENSION_ID))
        assertEquals("current", registry.commit(currentLease) { "current" })
    }

    private fun principal() = ExtensionPrincipal(
        extensionId = EXTENSION_ID,
        packageName = PACKAGE_NAME,
        serviceName = SERVICE_NAME,
        certificateSha256 = CERTIFICATE_SHA256,
        uid = UID,
    )

    private fun account() = ProviderAccount(
        id = "account-1",
        providerId = EXTENSION_ID.value,
        providerKind = "reference",
        baseUrl = "https://example.test",
        serverId = "server-1",
        serverName = "Server",
        serverVersion = "1.0",
        userId = "user-1",
        username = "viewer",
        playlistUrl = "m3u-provider://account/account-1/live",
        ownerPackageName = PACKAGE_NAME,
        ownerServiceName = SERVICE_NAME,
        ownerCertificateSha256 = CERTIFICATE_SHA256,
    )

    private companion object {
        val EXTENSION_ID = ExtensionId("com.m3u.example.provider")
        val OTHER_EXTENSION_ID = ExtensionId("com.m3u.other.provider")
        const val PACKAGE_NAME = "com.m3u.example.extension"
        const val OTHER_PACKAGE_NAME = "com.m3u.other.extension"
        const val SERVICE_NAME = "com.m3u.example.extension.ProviderService"
        const val OTHER_SERVICE_NAME = "com.m3u.example.extension.OtherService"
        const val CERTIFICATE_SHA256 = "certificate-a"
        const val OTHER_CERTIFICATE_SHA256 = "certificate-b"
        const val UID = 10_001
        const val OTHER_UID = 10_002
    }
}
