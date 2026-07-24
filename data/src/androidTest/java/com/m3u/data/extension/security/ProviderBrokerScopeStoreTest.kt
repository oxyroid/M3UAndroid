package com.m3u.data.extension.security

import com.m3u.data.database.model.ProviderAccount
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.extension.api.ExtensionHookIds
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.security.ContextReference
import com.m3u.extension.api.security.CredentialHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBrokerScopeStoreTest {
    @Test
    fun authenticationScopeConsumesTransientCredentialsAndLimitsEveryAccess() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val clock = TestClock(now = 1_000L)
        val principal = principal()
        registry.activate(principal)
        val password = vault.stage("password")
        val store = store(vault, registry, clock)

        val scope = store.mintAuthenticationScope(
            principal = principal,
            approvedBaseUrl = "https://Media.Example.test/emby?api_key=ignored",
            transientCredentials = mapOf("password" to password),
        )

        val access = store.authorize(
            scope = scope,
            principal = principal,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
        )
        assertEquals(ProviderBrokerScopeKind.AUTHENTICATION, access.kind)
        assertEquals("https://media.example.test:443", access.approvedOrigin)
        assertEquals(setOf(password), access.credentialHandles)
        assertEquals(61_000L, access.expiresAtEpochMillis)
        assertFalse(vault.hasTransient(password))
        assertEquals(
            "password",
            store.resolveCredential(
                scope = scope,
                principal = principal,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                handle = password,
            ),
        )

        expectFailure<SecurityException> {
            store.authorize(scope, principal, ExtensionHookIds.SubscriptionContentRefresh)
        }
        expectFailure<SecurityException> {
            store.resolveCredential(
                scope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
                CredentialHandle("transient:outside-scope"),
            )
        }
    }

    @Test
    fun missingTransientCredentialLeavesEveryOtherHandleAvailable() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        val available = vault.stage("available-password")
        val missing = CredentialHandle("transient:missing")
        val store = store(vault, registry)

        expectFailure<IllegalStateException> {
            store.mintAuthenticationScope(
                principal = principal,
                approvedBaseUrl = "https://media.example.test",
                transientCredentials = mapOf(
                    "available" to available,
                    "missing" to missing,
                ),
            )
        }

        assertTrue(vault.hasTransient(available))
        val scope = store.mintAuthenticationScope(
            principal = principal,
            approvedBaseUrl = "https://media.example.test",
            transientCredentials = mapOf("available" to available),
        )
        assertEquals(
            "available-password",
            store.resolveCredential(
                scope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
                available,
            ),
        )
    }

    @Test
    fun onlyCredentialCapturedByAuthenticationScopeCanAdvanceToInitialRefresh() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        val password = vault.stage("password")
        val store = store(vault, registry)
        val authenticationScope = store.mintAuthenticationScope(
            principal = principal,
            approvedBaseUrl = "https://media.example.test",
            transientCredentials = mapOf("password" to password),
        )
        val receipt = store.recordAuthentication(
            scope = authenticationScope,
            principal = principal,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            primaryCredential = "server-token",
            opaqueContexts = mapOf("user_id" to "remote-user"),
        )
        val capturedToken = store.consumeAuthenticationReceipt(
            scope = authenticationScope,
            principal = principal,
            receipt = receipt,
        ).credentialHandle

        expectFailure<SecurityException> {
            store.consumeAuthenticationReceipt(authenticationScope, principal, receipt)
        }

        assertTrue(capturedToken.value.startsWith("broker-captured:"))
        assertEquals(
            "server-token",
            store.resolveCredential(
                authenticationScope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
                capturedToken,
            ),
        )
        expectFailure<IllegalStateException> {
            store.advanceToInitialRefresh(authenticationScope, principal, password)
        }

        val refreshScope = store.advanceToInitialRefresh(
            authenticationScope = authenticationScope,
            principal = principal,
            capturedHandle = capturedToken,
        )

        assertNotEquals(authenticationScope, refreshScope)
        expectFailure<SecurityException> {
            store.authorize(
                authenticationScope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
            )
        }
        val access = store.authorize(
            refreshScope,
            principal,
            ExtensionHookIds.SubscriptionContentRefresh,
        )
        assertEquals(ProviderBrokerScopeKind.INITIAL_REFRESH, access.kind)
        assertEquals(setOf(capturedToken), access.credentialHandles)
        expectFailure<SecurityException> {
            store.resolveCredential(
                refreshScope,
                principal,
                ExtensionHookIds.SubscriptionContentRefresh,
                password,
            )
        }

        val material = ProviderCredentialMaterial.decode(
            store.completeInitialRefresh(refreshScope, principal, capturedToken)
        )
        assertEquals("server-token", material.primaryCredential)
        assertEquals(mapOf("user_id" to "remote-user"), material.opaqueContexts)
        expectFailure<SecurityException> {
            store.authorize(
                refreshScope,
                principal,
                ExtensionHookIds.SubscriptionContentRefresh,
            )
        }
    }

    @Test
    fun authenticationReceiptCannotAdvanceAnotherAuthenticationScope() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        val store = store(vault, registry)
        val firstScope = store.mintAuthenticationScope(
            principal,
            "https://first.example.test",
            mapOf("password" to vault.stage("first-password")),
        )
        val secondScope = store.mintAuthenticationScope(
            principal,
            "https://second.example.test",
            mapOf("password" to vault.stage("second-password")),
        )
        val receipt = store.recordAuthentication(
            scope = firstScope,
            principal = principal,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            primaryCredential = "first-token",
            opaqueContexts = emptyMap(),
        )
        expectFailure<SecurityException> {
            store.consumeAuthenticationReceipt(secondScope, principal, receipt)
        }
        val capturedToken = store.consumeAuthenticationReceipt(
            scope = firstScope,
            principal = principal,
            receipt = receipt,
        ).credentialHandle

        expectFailure<IllegalStateException> {
            store.advanceToInitialRefresh(secondScope, principal, capturedToken)
        }

        assertEquals(
            ProviderBrokerScopeKind.AUTHENTICATION,
            store.authorize(
                firstScope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
            ).kind,
        )
        assertEquals(
            ProviderBrokerScopeKind.AUTHENTICATION,
            store.authorize(
                secondScope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
            ).kind,
        )
    }

    @Test
    fun accountScopeDecryptsOnlyCredentialOwnedByActivePrincipal() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        val store = store(vault, registry)
        val account = account(principal)
        val credential = vault.persist(
            accountId = account.id,
            handle = "persistent-token",
            secret = ProviderCredentialMaterial(
                primaryCredential = "account-secret",
                opaqueContexts = mapOf("user_id" to "remote-user"),
            ).encode(),
        )

        val scope = store.mintAccountScope(
            principal = principal,
            allowedHook = ExtensionHookIds.PlaybackSourceResolve,
            account = account,
            credential = credential,
        )

        val access = store.authorize(
            scope,
            principal,
            ExtensionHookIds.PlaybackSourceResolve,
        )
        assertEquals(ProviderBrokerScopeKind.ACCOUNT, access.kind)
        assertEquals(account.id, access.accountId)
        assertEquals("https://media.example.test:8443", access.approvedOrigin)
        assertEquals(setOf("user_id"), access.opaqueContextKeys)
        assertEquals(
            "account-secret",
            store.resolveCredential(
                scope,
                principal,
                ExtensionHookIds.PlaybackSourceResolve,
                CredentialHandle(credential.credentialHandle),
            ),
        )
        assertEquals(
            "remote-user",
            store.resolveContext(
                scope,
                principal,
                ExtensionHookIds.PlaybackSourceResolve,
                ContextReference("user_id"),
            ),
        )
        assertEquals("legacy-secret", ProviderCredentialMaterial.decode("legacy-secret").primaryCredential)
        expectFailure<IllegalStateException> {
            store.recordAuthentication(
                scope = scope,
                principal = principal,
                hook = ExtensionHookIds.PlaybackSourceResolve,
                primaryCredential = "replacement-token",
                opaqueContexts = emptyMap(),
            )
        }
        expectFailure<SecurityException> {
            store.authorize(scope, principal, ExtensionHookIds.SubscriptionContentRefresh)
        }
        expectFailure<IllegalArgumentException> {
            store.mintAccountScope(
                principal,
                ExtensionHookIds.PlaybackSourceResolve,
                account.copy(ownerCertificateSha256 = "different-certificate"),
                credential,
            )
        }
        expectFailure<IllegalArgumentException> {
            store.mintAccountScope(
                principal,
                ExtensionHookIds.PlaybackSourceResolve,
                account,
                credential.copy(accountId = "another-account"),
            )
        }
    }

    @Test
    fun accountNetworkScopeExposesOnlyTheOwnedAccountOrigin() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        val store = store(vault, registry)
        val account = account(principal)

        val scope = store.mintAccountNetworkScope(
            principal = principal,
            allowedHook = ExtensionHookIds.SearchProviderQuery,
            account = account,
        )

        val access = store.authorize(
            scope,
            principal,
            ExtensionHookIds.SearchProviderQuery,
        )
        assertEquals(ProviderBrokerScopeKind.ACCOUNT, access.kind)
        assertEquals(account.id, access.accountId)
        assertEquals("https://media.example.test:8443", access.approvedOrigin)
        assertTrue(access.credentialHandles.isEmpty())
        assertTrue(access.opaqueContextKeys.isEmpty())
        expectFailure<SecurityException> {
            store.resolveCredential(
                scope,
                principal,
                ExtensionHookIds.SearchProviderQuery,
                CredentialHandle("persistent-token"),
            )
        }
        expectFailure<SecurityException> {
            store.resolveContext(
                scope,
                principal,
                ExtensionHookIds.SearchProviderQuery,
                ContextReference("user_id"),
            )
        }
    }

    @Test
    fun inactiveOrReplacedPrincipalCannotMintOrReuseScope() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val original = principal()
        val replacement = original.copy(
            packageName = "com.example.provider.reinstalled",
            serviceName = "com.example.provider.reinstalled.ExtensionService",
            certificateSha256 = "22".repeat(32),
            uid = 20_002,
        )
        val store = store(vault, registry)
        val unusedPassword = vault.stage("unused-password")

        expectFailure<SecurityException> {
            store.mintAuthenticationScope(
                original,
                "https://media.example.test",
                mapOf("password" to unusedPassword),
            )
        }
        assertTrue(vault.hasTransient(unusedPassword))

        registry.activate(original)
        val scope = store.mintAuthenticationScope(
            original,
            "https://media.example.test",
            mapOf("password" to unusedPassword),
        )
        assertEquals(
            original,
            registry.deactivate(
                original.extensionId,
                original.packageName,
                original.serviceName,
            ),
        )
        registry.activate(replacement)

        expectFailure<SecurityException> {
            store.authorize(
                scope,
                original,
                ExtensionHookIds.SubscriptionProviderValidate,
            )
        }
        expectFailure<SecurityException> {
            store.authorize(
                scope,
                replacement,
                ExtensionHookIds.SubscriptionProviderValidate,
            )
        }
        assertEquals(1, store.closeAll(original))
        assertEquals(0, store.closeAll(original))
    }

    @Test
    fun ttlAndCapacityAreEnforcedBeforeTransientCredentialConsumption() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val clock = TestClock(now = 5_000L)
        val principal = principal()
        registry.activate(principal)
        val store = store(
            vault = vault,
            registry = registry,
            clock = clock,
            defaultTtlMillis = 100L,
            maximumTtlMillis = 100L,
            maximumScopes = 1,
        )
        val firstPassword = vault.stage("first-password")
        val secondPassword = vault.stage("second-password")
        val firstScope = store.mintAuthenticationScope(
            principal,
            "https://media.example.test",
            mapOf("password" to firstPassword),
        )

        expectFailure<IllegalStateException> {
            store.mintAuthenticationScope(
                principal,
                "https://media.example.test",
                mapOf("password" to secondPassword),
            )
        }
        assertTrue(vault.hasTransient(secondPassword))

        clock.now += 100L
        expectFailure<SecurityException> {
            store.authorize(
                firstScope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
            )
        }
        val secondScope = store.mintAuthenticationScope(
            principal,
            "http://[2001:db8::1]:8096/emby",
            mapOf("password" to secondPassword),
        )
        assertFalse(vault.hasTransient(secondPassword))
        assertEquals(
            "http://[2001:db8::1]:8096",
            store.authorize(
                secondScope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
            ).approvedOrigin,
        )
        assertTrue(store.close(secondScope))
        assertFalse(store.close(secondScope))

        val thirdPassword = vault.stage("third-password")
        expectFailure<IllegalArgumentException> {
            store.mintAuthenticationScope(
                principal,
                "https://media.example.test",
                mapOf("password" to thirdPassword),
                ttlMillis = 101L,
            )
        }
        assertTrue(vault.hasTransient(thirdPassword))
    }

    @Test
    fun authenticationScopeAllowsOnlyOneHostAuthenticationResult() {
        val vault = FakeCredentialVault()
        val registry = ActiveExtensionPrincipalRegistry()
        val principal = principal()
        registry.activate(principal)
        val store = store(
            vault = vault,
            registry = registry,
            maximumCredentialsPerScope = 2,
        )
        val scope = store.mintAuthenticationScope(
            principal,
            "https://media.example.test",
            mapOf("password" to vault.stage("password")),
        )

        val receipt = store.recordAuthentication(
            scope = scope,
            principal = principal,
            hook = ExtensionHookIds.SubscriptionProviderValidate,
            primaryCredential = "token",
            opaqueContexts = emptyMap(),
        )
        val captured = store.consumeAuthenticationReceipt(
            scope = scope,
            principal = principal,
            receipt = receipt,
        ).credentialHandle
        expectFailure<IllegalStateException> {
            store.recordAuthentication(
                scope = scope,
                principal = principal,
                hook = ExtensionHookIds.SubscriptionProviderValidate,
                primaryCredential = "second-token",
                opaqueContexts = emptyMap(),
            )
        }

        assertEquals(
            "token",
            store.resolveCredential(
                scope,
                principal,
                ExtensionHookIds.SubscriptionProviderValidate,
                captured,
            ),
        )
    }

    private fun principal() = ExtensionPrincipal(
        extensionId = ExtensionId("com.example.provider"),
        packageName = "com.example.provider",
        serviceName = "com.example.provider.ExtensionService",
        certificateSha256 = "11".repeat(32),
        uid = 10_001,
    )

    private fun account(principal: ExtensionPrincipal) = ProviderAccount(
        id = "account-1",
        providerId = principal.extensionId.value,
        providerKind = "example",
        baseUrl = "https://media.example.test:8443/library",
        serverId = "server-1",
        serverName = "Example server",
        serverVersion = "1.0",
        userId = "user-1",
        username = "viewer",
        playlistUrl = "provider://account-1",
        ownerPackageName = principal.packageName,
        ownerServiceName = principal.serviceName,
        ownerCertificateSha256 = principal.certificateSha256,
    )

    private fun store(
        vault: FakeCredentialVault,
        registry: ActiveExtensionPrincipalRegistry,
        clock: TestClock = TestClock(now = 1_000L),
        defaultTtlMillis: Long = 60_000L,
        maximumTtlMillis: Long = defaultTtlMillis,
        maximumScopes: Int = 16,
        maximumCredentialsPerScope: Int = 16,
    ): ProviderBrokerScopeStore {
        var id = 0
        return ProviderBrokerScopeStore(
            credentialVault = vault,
            principalRegistry = registry,
            clock = clock::read,
            idFactory = { "test-${id++}" },
            defaultTtlMillis = defaultTtlMillis,
            maximumTtlMillis = maximumTtlMillis,
            maximumScopes = maximumScopes,
            maximumCredentialsPerScope = maximumCredentialsPerScope,
        )
    }

    private class TestClock(var now: Long) {
        fun read(): Long = now
    }

    private class FakeCredentialVault : CredentialVault {
        private val transientSecrets = linkedMapOf<String, String>()
        private val persistentSecrets = linkedMapOf<Pair<String, String>, String>()
        private var nextTransientId = 0
        private var nextPersistentId = 0

        override fun encrypt(
            accountId: String,
            secret: String,
            credentialHandle: String?,
        ): ProviderCredentialEntity = persist(
            accountId = accountId,
            handle = credentialHandle ?: "persistent-${nextPersistentId++}",
            secret = secret,
        )

        override fun decrypt(credential: ProviderCredentialEntity): String? =
            persistentSecrets[credential.accountId to credential.credentialHandle]

        override fun stage(secret: String): CredentialHandle {
            val handle = CredentialHandle("transient:${nextTransientId++}")
            transientSecrets[handle.value] = secret
            return handle
        }

        override fun consume(handle: CredentialHandle): String? = transientSecrets.remove(handle.value)

        override fun consumeAll(
            handles: Set<CredentialHandle>,
        ): Map<CredentialHandle, String>? {
            if (handles.any { handle -> handle.value !in transientSecrets }) return null
            return handles.associateWith { handle ->
                checkNotNull(transientSecrets.remove(handle.value))
            }
        }

        fun hasTransient(handle: CredentialHandle): Boolean = handle.value in transientSecrets

        fun persist(
            accountId: String,
            handle: String,
            secret: String,
        ): ProviderCredentialEntity {
            persistentSecrets[accountId to handle] = secret
            return ProviderCredentialEntity(
                accountId = accountId,
                credentialHandle = handle,
                ciphertext = "fake-ciphertext",
                nonce = "fake-nonce",
                keyVersion = 1,
            )
        }
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
        } catch (failure: Throwable) {
            if (failure is T) return failure
            throw AssertionError(
                "Expected ${T::class.java.simpleName}, but got ${failure::class.java.simpleName}",
                failure,
            )
        }
        throw AssertionError("Expected ${T::class.java.simpleName}, but no exception was thrown")
    }
}
