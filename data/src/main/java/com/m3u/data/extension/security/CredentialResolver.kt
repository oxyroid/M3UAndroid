package com.m3u.data.extension.security

import com.m3u.data.database.dao.ProviderDao
import com.m3u.extension.api.security.CredentialHandle
import javax.inject.Inject

internal class CredentialResolver @Inject constructor(
    private val providerDao: ProviderDao,
    private val credentialVault: CredentialVault,
) {
    suspend fun resolve(handle: CredentialHandle): String? {
        credentialVault.consume(handle)?.let { return it }
        val credential = providerDao.getCredentialByHandle(handle.value) ?: return null
        return credentialVault.decrypt(credential)
    }

    fun stage(secret: String): CredentialHandle = credentialVault.stage(secret)
}
