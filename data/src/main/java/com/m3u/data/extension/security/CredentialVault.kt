package com.m3u.data.extension.security

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.extension.api.security.CredentialHandle
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

interface CredentialVault {
    fun encrypt(accountId: String, secret: String, credentialHandle: String? = null): ProviderCredentialEntity
    fun decrypt(credential: ProviderCredentialEntity): String?
    fun stage(secret: String): CredentialHandle
    fun consume(handle: CredentialHandle): String?

    /** Returns and removes every requested secret, or leaves all of them untouched. */
    fun consumeAll(handles: Set<CredentialHandle>): Map<CredentialHandle, String>? = when {
        handles.isEmpty() -> emptyMap()
        handles.size == 1 -> handles.single().let { handle ->
            consume(handle)?.let { secret -> mapOf(handle to secret) }
        }
        else -> null
    }
}

interface ExtensionSecretStore {
    fun store(
        extensionId: String,
        settingKey: String,
        secret: String,
        existingHandle: CredentialHandle? = null,
    ): CredentialHandle

    fun resolve(extensionId: String, handle: CredentialHandle): String?
    fun delete(extensionId: String, handle: CredentialHandle)
    fun clear(extensionId: String)
}

@Singleton
internal class AndroidKeystoreCredentialVault private constructor(
    context: Context,
    transientConfiguration: TransientCredentialConfiguration,
    private val keyAlias: String,
) : CredentialVault, ExtensionSecretStore {
    private val transientSecrets = linkedMapOf<String, TransientCredential>()
    private val transientClock = transientConfiguration.clock
    private val transientTtlMillis = transientConfiguration.ttlMillis
    private val maximumTransientCredentials = transientConfiguration.maximumCredentials
    private val extensionSecrets = context.getSharedPreferences(
        EXTENSION_SECRET_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        context = context,
        transientConfiguration = TransientCredentialConfiguration(
            clock = SystemClock::elapsedRealtime,
            ttlMillis = TRANSIENT_CREDENTIAL_TTL_MILLIS,
            maximumCredentials = MAXIMUM_TRANSIENT_CREDENTIALS,
        ),
        keyAlias = KEY_ALIAS,
    )

    internal constructor(
        context: Context,
        transientClock: () -> Long,
        transientTtlMillis: Long,
        maximumTransientCredentials: Int,
    ) : this(
        context = context,
        transientConfiguration = TransientCredentialConfiguration(
            clock = transientClock,
            ttlMillis = transientTtlMillis,
            maximumCredentials = maximumTransientCredentials,
        ),
        keyAlias = KEY_ALIAS,
    )

    internal constructor(
        context: Context,
        keyAlias: String,
    ) : this(
        context = context,
        transientConfiguration = TransientCredentialConfiguration(
            clock = SystemClock::elapsedRealtime,
            ttlMillis = TRANSIENT_CREDENTIAL_TTL_MILLIS,
            maximumCredentials = MAXIMUM_TRANSIENT_CREDENTIALS,
        ),
        keyAlias = keyAlias,
    )

    init {
        require(transientTtlMillis > 0) { "Transient credential TTL must be positive" }
        require(maximumTransientCredentials > 0) {
            "Transient credential capacity must be positive"
        }
    }

    @Synchronized
    override fun stage(secret: String): CredentialHandle {
        secret.requireWithinHostLimit()
        val now = transientClock()
        purgeExpiredTransientCredentials(now)
        check(transientSecrets.size < maximumTransientCredentials) {
            "Transient credential capacity is exhausted"
        }
        val expiresAt = try {
            Math.addExact(now, transientTtlMillis)
        } catch (_: ArithmeticException) {
            error("Transient credential expiry overflow")
        }
        repeat(MAXIMUM_TRANSIENT_HANDLE_GENERATION_ATTEMPTS) {
            val handle = CredentialHandle("transient:${UUID.randomUUID()}")
            if (handle.value !in transientSecrets) {
                transientSecrets[handle.value] = TransientCredential(secret, expiresAt)
                return handle
            }
        }
        error("Unable to mint a unique transient credential handle")
    }

    @Synchronized
    override fun consume(handle: CredentialHandle): String? {
        val credential = transientSecrets.remove(handle.value) ?: return null
        return credential.secret.takeIf { credential.expiresAtEpochMillis > transientClock() }
    }

    @Synchronized
    override fun consumeAll(
        handles: Set<CredentialHandle>,
    ): Map<CredentialHandle, String>? {
        purgeExpiredTransientCredentials(transientClock())
        if (handles.any { handle -> !transientSecrets.containsKey(handle.value) }) return null
        return handles.associateWith { handle ->
            checkNotNull(transientSecrets.remove(handle.value)).secret
        }
    }

    private fun purgeExpiredTransientCredentials(now: Long) {
        transientSecrets.entries.removeAll { (_, credential) ->
            credential.expiresAtEpochMillis <= now
        }
    }

    override fun encrypt(
        accountId: String,
        secret: String,
        credentialHandle: String?,
    ): ProviderCredentialEntity {
        val handle = credentialHandle ?: UUID.randomUUID().toString()
        val plaintext = secret.requireWithinHostLimit()
        val encrypted = encryptBytes(
            plaintext = plaintext,
            associatedData = aad(accountId, handle),
        )
        return ProviderCredentialEntity(
            accountId = accountId,
            credentialHandle = handle,
            ciphertext = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP),
            nonce = Base64.encodeToString(encrypted.nonce, Base64.NO_WRAP),
            keyVersion = KEY_VERSION,
        )
    }

    override fun decrypt(credential: ProviderCredentialEntity): String? = runCatching {
        if (credential.keyVersion != KEY_VERSION) return null
        val nonce = Base64.decode(credential.nonce, Base64.NO_WRAP)
        val plaintext = decryptBytes(
            ciphertext = Base64.decode(credential.ciphertext, Base64.NO_WRAP),
            nonce = nonce,
            associatedData = aad(credential.accountId, credential.credentialHandle),
        ) ?: return null
        String(plaintext, StandardCharsets.UTF_8)
    }.getOrNull()

    @Synchronized
    override fun store(
        extensionId: String,
        settingKey: String,
        secret: String,
        existingHandle: CredentialHandle?,
    ): CredentialHandle {
        require(extensionId.isNotBlank()) { "Extension id must not be blank" }
        require(settingKey.isNotBlank()) { "Setting key must not be blank" }
        val plaintext = secret.requireWithinHostLimit()
        val reusableHandle = existingHandle?.takeIf { handle ->
            extensionSecrets.getString("${handle.value}:owner", null) == extensionId &&
                extensionSecrets.getString("${handle.value}:key", null) == settingKey
        }
        val handle = reusableHandle ?: CredentialHandle("extension-secret:${UUID.randomUUID()}")
        val encrypted = encryptBytes(
            plaintext = plaintext,
            associatedData = extensionAad(extensionId, settingKey, handle.value),
        )
        extensionSecrets.edit()
            .putString("${handle.value}:owner", extensionId)
            .putString("${handle.value}:key", settingKey)
            .putString(
                "${handle.value}:ciphertext",
                Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP),
            )
            .putString(
                "${handle.value}:nonce",
                Base64.encodeToString(encrypted.nonce, Base64.NO_WRAP),
            )
            .putInt("${handle.value}:version", KEY_VERSION)
            .apply()
        return handle
    }

    @Synchronized
    override fun resolve(extensionId: String, handle: CredentialHandle): String? {
        val owner = extensionSecrets.getString("${handle.value}:owner", null)
        if (owner != extensionId) return null
        val settingKey = extensionSecrets.getString("${handle.value}:key", null) ?: return null
        val version = extensionSecrets.getInt("${handle.value}:version", -1)
        if (version != KEY_VERSION) {
            delete(extensionId, handle)
            return null
        }
        return runCatching {
            val ciphertext = checkNotNull(
                extensionSecrets.getString("${handle.value}:ciphertext", null)
            )
            val nonce = checkNotNull(extensionSecrets.getString("${handle.value}:nonce", null))
            val plaintext = decryptBytes(
                ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
                nonce = Base64.decode(nonce, Base64.NO_WRAP),
                associatedData = extensionAad(extensionId, settingKey, handle.value),
            ) ?: error("Extension credential key is unavailable")
            String(plaintext, StandardCharsets.UTF_8)
        }.getOrElse {
            delete(extensionId, handle)
            null
        }
    }

    @Synchronized
    override fun delete(extensionId: String, handle: CredentialHandle) {
        if (extensionSecrets.getString("${handle.value}:owner", null) != extensionId) return
        extensionSecrets.edit()
            .remove("${handle.value}:owner")
            .remove("${handle.value}:key")
            .remove("${handle.value}:ciphertext")
            .remove("${handle.value}:nonce")
            .remove("${handle.value}:version")
            .apply()
    }

    @Synchronized
    override fun clear(extensionId: String) {
        val handles = extensionSecrets.all
            .filter { (key, value) -> key.endsWith(":owner") && value == extensionId }
            .keys
            .map { key -> CredentialHandle(key.removeSuffix(":owner")) }
        handles.forEach { handle -> delete(extensionId, handle) }
    }

    private fun encryptBytes(
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedCredential = synchronized(KEY_LIFECYCLE_LOCK) {
        try {
            encryptBytesLocked(
                key = getOrCreateKeyLocked(),
                plaintext = plaintext,
                associatedData = associatedData,
            )
        } catch (failure: Throwable) {
            if (!failure.isUnusableCredentialKey()) throw failure
            deleteKeyLocked()
            try {
                encryptBytesLocked(
                    key = getOrCreateKeyLocked(),
                    plaintext = plaintext,
                    associatedData = associatedData,
                )
            } catch (retryFailure: Throwable) {
                if (retryFailure.isUnusableCredentialKey()) {
                    runCatching { deleteKeyLocked() }
                }
                throw retryFailure
            }
        }
    }

    private fun decryptBytes(
        ciphertext: ByteArray,
        nonce: ByteArray,
        associatedData: ByteArray,
    ): ByteArray? = synchronized(KEY_LIFECYCLE_LOCK) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKeyLocked(),
                GCMParameterSpec(GCM_TAG_BITS, nonce),
            )
            cipher.updateAAD(associatedData)
            cipher.doFinal(ciphertext)
        } catch (failure: Throwable) {
            if (!failure.isUnusableCredentialKey()) throw failure
            runCatching { deleteKeyLocked() }
            null
        }
    }

    private fun encryptBytesLocked(
        key: SecretKey,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedCredential {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(associatedData)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedCredential(ciphertext = ciphertext, nonce = cipher.iv)
    }

    private fun getOrCreateKeyLocked(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            return keyStore.getKey(keyAlias, null) as? SecretKey
                ?: throw CredentialKeyUnavailableException()
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    private fun deleteKeyLocked() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun aad(accountId: String, handle: String): ByteArray =
        "$accountId:$handle:$KEY_VERSION".toByteArray(StandardCharsets.UTF_8)

    private fun extensionAad(extensionId: String, settingKey: String, handle: String): ByteArray =
        "extension:$extensionId:$settingKey:$handle:$KEY_VERSION"
            .toByteArray(StandardCharsets.UTF_8)

    private fun String.requireWithinHostLimit(): ByteArray =
        toByteArray(StandardCharsets.UTF_8).also { encoded ->
            require(encoded.size <= MAXIMUM_SECRET_BYTES) {
                "Credential exceeds the host limit"
            }
        }

    private companion object {
        // The alias is process-global. Keep lookup, use, invalidation, and replacement atomic
        // across every vault instance so recovery cannot delete a key during another Cipher call.
        val KEY_LIFECYCLE_LOCK = Any()
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "m3u.provider.credentials.v1"
        const val EXTENSION_SECRET_PREFERENCES = "extension-setting-secrets"
        const val KEY_VERSION = 1
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val TRANSIENT_CREDENTIAL_TTL_MILLIS = 5 * 60 * 1_000L
        const val MAXIMUM_TRANSIENT_CREDENTIALS = 128
        const val MAXIMUM_TRANSIENT_HANDLE_GENERATION_ATTEMPTS = 16
        const val MAXIMUM_SECRET_BYTES = 64 * 1024
    }
}

private data class EncryptedCredential(
    val ciphertext: ByteArray,
    val nonce: ByteArray,
)

private class CredentialKeyUnavailableException : InvalidKeyException()

private fun Throwable.isUnusableCredentialKey(): Boolean =
    generateSequence(this) { failure -> failure.cause }
        .any { failure ->
            failure is CredentialKeyUnavailableException ||
                failure is KeyPermanentlyInvalidatedException ||
                failure is UnrecoverableKeyException ||
                failure is InvalidKeyException
        }

private data class TransientCredentialConfiguration(
    val clock: () -> Long,
    val ttlMillis: Long,
    val maximumCredentials: Int,
)

private data class TransientCredential(
    val secret: String,
    val expiresAtEpochMillis: Long,
)
