package com.m3u.data.extension.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.content.Context
import android.util.Base64
import com.m3u.data.database.model.ProviderCredentialEntity
import com.m3u.extension.api.security.CredentialHandle
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
internal class AndroidKeystoreCredentialVault @Inject constructor(
    @ApplicationContext context: Context,
) : CredentialVault, ExtensionSecretStore {
    private val transientSecrets = ConcurrentHashMap<String, String>()
    private val extensionSecrets = context.getSharedPreferences(
        EXTENSION_SECRET_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    override fun stage(secret: String): CredentialHandle {
        val handle = CredentialHandle("transient:${UUID.randomUUID()}")
        transientSecrets[handle.value] = secret
        return handle
    }

    override fun consume(handle: CredentialHandle): String? = transientSecrets.remove(handle.value)

    override fun encrypt(
        accountId: String,
        secret: String,
        credentialHandle: String?,
    ): ProviderCredentialEntity {
        val handle = credentialHandle ?: UUID.randomUUID().toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(aad(accountId, handle))
        val ciphertext = cipher.doFinal(secret.toByteArray(StandardCharsets.UTF_8))
        return ProviderCredentialEntity(
            accountId = accountId,
            credentialHandle = handle,
            ciphertext = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            nonce = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            keyVersion = KEY_VERSION,
        )
    }

    override fun decrypt(credential: ProviderCredentialEntity): String? = runCatching {
        if (credential.keyVersion != KEY_VERSION) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val nonce = Base64.decode(credential.nonce, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, nonce))
        cipher.updateAAD(aad(credential.accountId, credential.credentialHandle))
        val plaintext = cipher.doFinal(Base64.decode(credential.ciphertext, Base64.NO_WRAP))
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
        val reusableHandle = existingHandle?.takeIf { handle ->
            extensionSecrets.getString("${handle.value}:owner", null) == extensionId &&
                extensionSecrets.getString("${handle.value}:key", null) == settingKey
        }
        val handle = reusableHandle ?: CredentialHandle("extension-secret:${UUID.randomUUID()}")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(extensionAad(extensionId, settingKey, handle.value))
        val ciphertext = cipher.doFinal(secret.toByteArray(StandardCharsets.UTF_8))
        extensionSecrets.edit()
            .putString("${handle.value}:owner", extensionId)
            .putString("${handle.value}:key", settingKey)
            .putString("${handle.value}:ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString("${handle.value}:nonce", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
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
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(nonce, Base64.NO_WRAP)),
            )
            cipher.updateAAD(extensionAad(extensionId, settingKey, handle.value))
            String(
                cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)),
                StandardCharsets.UTF_8,
            )
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

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
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

    private fun aad(accountId: String, handle: String): ByteArray =
        "$accountId:$handle:$KEY_VERSION".toByteArray(StandardCharsets.UTF_8)

    private fun extensionAad(extensionId: String, settingKey: String, handle: String): ByteArray =
        "extension:$extensionId:$settingKey:$handle:$KEY_VERSION"
            .toByteArray(StandardCharsets.UTF_8)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "m3u.provider.credentials.v1"
        const val EXTENSION_SECRET_PREFERENCES = "extension-setting-secrets"
        const val KEY_VERSION = 1
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
