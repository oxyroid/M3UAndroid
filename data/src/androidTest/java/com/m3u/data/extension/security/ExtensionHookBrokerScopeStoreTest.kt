package com.m3u.data.extension.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.security.CredentialHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionHookBrokerScopeStoreTest {
    @Test
    fun scopeBindsPrincipalHookOriginsAndOnlyItsSecrets() {
        val fixture = fixture()
        val apiKey = CredentialHandle("extension-secret:api-key")
        val scope = fixture.store.mintHookScope(
            principal = fixture.principal,
            allowedHook = ExtensionHookIds.SearchProviderQuery,
            approvedOrigins = setOf(
                "https://search.example.test",
                "http://metadata.example.test:8080",
            ),
            credentials = mapOf(apiKey to "opaque-api-key"),
        )

        val access = fixture.store.authorize(
            scope = scope,
            principal = fixture.principal,
            hook = ExtensionHookIds.SearchProviderQuery,
        )

        assertEquals(
            setOf(
                "https://search.example.test:443",
                "http://metadata.example.test:8080",
            ),
            access.approvedOrigins,
        )
        assertEquals(
            "opaque-api-key",
            fixture.store.resolveCredential(
                scope,
                fixture.principal,
                ExtensionHookIds.SearchProviderQuery,
                apiKey,
            ),
        )
        assertThrows(SecurityException::class.java) {
            fixture.store.resolveCredential(
                scope,
                fixture.principal,
                ExtensionHookIds.SearchProviderQuery,
                CredentialHandle("extension-secret:another"),
            )
        }
        assertThrows(SecurityException::class.java) {
            fixture.store.authorize(
                scope,
                fixture.principal,
                ExtensionHookIds.MetadataChannelEnrich,
            )
        }
        assertThrows(SecurityException::class.java) {
            fixture.store.authorize(
                scope,
                fixture.otherPrincipal,
                ExtensionHookIds.SearchProviderQuery,
            )
        }
    }

    @Test
    fun closeAndExpiryRevokeGenericScope() {
        val fixture = fixture()
        val first = fixture.store.mintHookScope(
            principal = fixture.principal,
            allowedHook = ExtensionHookIds.BackgroundTaskRun,
            approvedOrigins = setOf("https://jobs.example.test"),
            credentials = emptyMap(),
        )
        assertTrue(fixture.store.close(first))
        assertFalse(fixture.store.close(first))
        assertThrows(SecurityException::class.java) {
            fixture.store.authorize(
                first,
                fixture.principal,
                ExtensionHookIds.BackgroundTaskRun,
            )
        }

        val expiring = fixture.store.mintHookScope(
            principal = fixture.principal,
            allowedHook = ExtensionHookIds.BackgroundTaskRun,
            approvedOrigins = setOf("https://jobs.example.test"),
            credentials = emptyMap(),
            ttlMillis = 10,
        )
        fixture.clock += 10
        assertThrows(SecurityException::class.java) {
            fixture.store.authorize(
                expiring,
                fixture.principal,
                ExtensionHookIds.BackgroundTaskRun,
            )
        }
    }

    @Test
    fun genericScopeRejectsNonExactDuplicateAndUnsupportedOrigins() {
        val fixture = fixture()
        listOf(
            setOf("https://api.example.test/path"),
            setOf("https://*.example.test"),
            setOf(
                "https://api.example.test",
                "https://api.example.test:443",
            ),
        ).forEach { origins ->
            assertThrows(IllegalArgumentException::class.java) {
                fixture.store.mintHookScope(
                    principal = fixture.principal,
                    allowedHook = ExtensionHookIds.SearchProviderQuery,
                    approvedOrigins = origins,
                    credentials = emptyMap(),
                )
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            fixture.store.mintHookScope(
                principal = fixture.principal,
                allowedHook = ExtensionHookIds.SubscriptionProviderDiscover,
                approvedOrigins = setOf("https://api.example.test"),
                credentials = emptyMap(),
            )
        }
    }

    private fun fixture(): Fixture {
        var clock = 1_000L
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal("com.example.scope", uid = 1001)
        val otherPrincipal = principal("com.example.other", uid = 1002)
        registry.activate(principal)
        registry.activate(otherPrincipal)
        val store = ProviderBrokerScopeStore(
            credentialVault = UnusedCredentialVault,
            principalRegistry = registry,
            clock = { clock },
            idFactory = sequenceOf("one", "two", "three", "four").iterator()::next,
            defaultTtlMillis = 100,
            maximumTtlMillis = 100,
        )
        return Fixture(
            store = store,
            principal = principal,
            otherPrincipal = otherPrincipal,
            clockValue = { clock },
            updateClock = { value -> clock = value },
        )
    }

    private fun principal(extensionId: String, uid: Int) = ExtensionPrincipal(
        extensionId = ExtensionId(extensionId),
        packageName = "$extensionId.package",
        serviceName = "$extensionId.Service",
        certificateSha256 = "certificate-$uid",
        uid = uid,
    )

    private class Fixture(
        val store: ProviderBrokerScopeStore,
        val principal: ExtensionPrincipal,
        val otherPrincipal: ExtensionPrincipal,
        private val clockValue: () -> Long,
        private val updateClock: (Long) -> Unit,
    ) {
        var clock: Long
            get() = clockValue()
            set(value) = updateClock(value)
    }

    private object UnusedCredentialVault : CredentialVault {
        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ): ProviderCredentialEntity = error("Not used")

        override fun decrypt(credential: ProviderCredentialEntity): String? = error("Not used")

        override fun stage(secret: String): CredentialHandle = error("Not used")

        override fun consume(handle: CredentialHandle): String? = error("Not used")
    }
}
