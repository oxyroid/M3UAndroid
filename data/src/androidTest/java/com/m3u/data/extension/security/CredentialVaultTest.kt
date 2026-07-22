package com.m3u.data.extension.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import com.m3u.extension.api.security.CredentialHandle
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialVaultTest {
    @Test
    fun bulkConsumeIsAtomic() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = AndroidKeystoreCredentialVault(context)
        val available = vault.stage("available-password")
        val missing = CredentialHandle("transient:missing")

        assertNull(vault.consumeAll(setOf(available, missing)))
        assertEquals("available-password", vault.consume(available))
    }

    @Test
    fun transientCredentialsAreBoundedAndExpire() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clock = TestClock(now = 10_000L)
        val vault = AndroidKeystoreCredentialVault(
            context = context,
            transientClock = clock::read,
            transientTtlMillis = 100L,
            maximumTransientCredentials = 1,
        )
        val expired = vault.stage("expired-password")

        expectFailure<IllegalStateException> {
            vault.stage("over-capacity-password")
        }
        clock.now += 100L
        assertNull(vault.consume(expired))

        val replacement = vault.stage("replacement-password")
        assertEquals("replacement-password", vault.consume(replacement))
    }

    @Test
    fun oversizedCredentialsAreRejectedBeforeStagingOrEncryption() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = AndroidKeystoreCredentialVault(context, testAlias())
        val oversized = "x".repeat(MAXIMUM_ACCEPTED_SECRET_BYTES + 1)

        expectFailure<IllegalArgumentException> { vault.stage(oversized) }
        expectFailure<IllegalArgumentException> {
            vault.encrypt(accountId = "provider-account", secret = oversized)
        }
        expectFailure<IllegalArgumentException> {
            vault.store(
                extensionId = "com.m3u.test.extension",
                settingKey = "password",
                secret = oversized,
            )
        }
    }

    @Test
    fun concurrentFirstUseAcrossVaultInstancesCreatesUsableCredentials() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alias = testAlias()
        val executor = Executors.newFixedThreadPool(CONCURRENT_WRITERS)
        deleteKey(alias)
        try {
            val start = CountDownLatch(1)
            val encrypted = (0 until CONCURRENT_WRITERS).map { index ->
                executor.submit(Callable {
                    check(start.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    AndroidKeystoreCredentialVault(context, alias).encrypt(
                        accountId = "account-$index",
                        secret = "secret-$index",
                    )
                })
            }
            start.countDown()

            val credentials = encrypted.map { future ->
                future.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            val verifier = AndroidKeystoreCredentialVault(context, alias)
            credentials.forEachIndexed { index, credential ->
                assertEquals("secret-$index", verifier.decrypt(credential))
            }
        } finally {
            executor.shutdownNow()
            deleteKey(alias)
        }
    }

    @Test
    fun unusableProviderKeyFailsClosedAndNextWriteRecreatesKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alias = testAlias()
        val vault = AndroidKeystoreCredentialVault(context, alias)
        deleteKey(alias)
        try {
            installIncompatibleKey(alias)
            val encrypted = vault.encrypt(
                accountId = "provider-account",
                secret = "current-provider-secret",
            )
            assertEquals("current-provider-secret", vault.decrypt(encrypted))

            installIncompatibleKey(alias)
            assertNull(vault.decrypt(encrypted))
            assertFalse(keyExists(alias))

            val replacement = vault.encrypt(
                accountId = "provider-account",
                secret = "replacement-provider-secret",
            )
            assertEquals("replacement-provider-secret", vault.decrypt(replacement))
        } finally {
            deleteKey(alias)
        }
    }

    @Test
    fun unusableExtensionKeyFailsClosedAndNextStoreRecreatesKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val alias = testAlias()
        val extensionId = "test.extension.${UUID.randomUUID()}"
        val vault = AndroidKeystoreCredentialVault(context, alias)
        deleteKey(alias)
        try {
            val oldHandle = vault.store(
                extensionId = extensionId,
                settingKey = "password",
                secret = "old-extension-secret",
            )
            installIncompatibleKey(alias)

            assertNull(vault.resolve(extensionId, oldHandle))
            assertFalse(keyExists(alias))

            installIncompatibleKey(alias)
            val replacementHandle = vault.store(
                extensionId = extensionId,
                settingKey = "password",
                secret = "replacement-extension-secret",
            )
            assertEquals(
                "replacement-extension-secret",
                vault.resolve(extensionId, replacementHandle),
            )
        } finally {
            vault.clear(extensionId)
            deleteKey(alias)
        }
    }

    private fun installIncompatibleKey(alias: String) {
        deleteKey(alias)
        KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEYSTORE,
        ).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
            )
            generateKey()
        }
    }

    private fun deleteKey(alias: String) {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }

    private fun keyExists(alias: String): Boolean =
        KeyStore.getInstance(ANDROID_KEYSTORE).run {
            load(null)
            containsAlias(alias)
        }

    private fun testAlias(): String = "m3u.test.credentials.${UUID.randomUUID()}"

    private class TestClock(var now: Long) {
        fun read(): Long = now
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

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val CONCURRENT_WRITERS = 8
        const val MAXIMUM_ACCEPTED_SECRET_BYTES = 64 * 1024
        const val TEST_TIMEOUT_SECONDS = 30L
    }
}
