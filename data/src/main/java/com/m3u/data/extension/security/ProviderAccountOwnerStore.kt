package com.m3u.data.extension.security

import androidx.room.withTransaction
import com.m3u.data.database.M3UDatabase
import com.m3u.data.database.dao.ProviderDao
import javax.inject.Inject
import javax.inject.Singleton

internal data class ExtensionOwnerIdentity(
    val extensionId: String,
    val packageName: String,
    val serviceName: String,
    val certificateSha256: String,
)

@Singleton
internal class ProviderAccountOwnerStore @Inject constructor(
    private val database: M3UDatabase,
    private val providerDao: ProviderDao,
) {
    suspend fun revoke(identity: ExtensionOwnerIdentity): ProviderOwnerRevocation =
        database.withTransaction {
            val credentials = providerDao.deleteCredentialsOwnedBy(
                identity.extensionId,
                identity.packageName,
                identity.serviceName,
                identity.certificateSha256,
            )
            val sessions = providerDao.deletePlaybackSessionsOwnedBy(
                identity.extensionId,
                identity.packageName,
                identity.serviceName,
                identity.certificateSha256,
            )
            val accounts = providerDao.requireReauthenticationForOwner(
                identity.extensionId,
                identity.packageName,
                identity.serviceName,
                identity.certificateSha256,
            )
            ProviderOwnerRevocation(
                affectedAccounts = accounts,
                deletedCredentials = credentials,
                deletedPlaybackSessions = sessions,
            )
        }

    suspend fun transfer(
        previous: ExtensionOwnerIdentity,
        replacement: ExtensionOwnerIdentity,
    ): ProviderOwnerRevocation {
        require(previous.extensionId == replacement.extensionId)
        require(previous != replacement)
        return database.withTransaction {
            val credentials = providerDao.deleteCredentialsOwnedBy(
                previous.extensionId,
                previous.packageName,
                previous.serviceName,
                previous.certificateSha256,
            )
            val sessions = providerDao.deletePlaybackSessionsOwnedBy(
                previous.extensionId,
                previous.packageName,
                previous.serviceName,
                previous.certificateSha256,
            )
            val accounts = providerDao.transferAccountOwner(
                extensionId = previous.extensionId,
                oldPackageName = previous.packageName,
                oldServiceName = previous.serviceName,
                oldCertificateSha256 = previous.certificateSha256,
                newPackageName = replacement.packageName,
                newServiceName = replacement.serviceName,
                newCertificateSha256 = replacement.certificateSha256,
            )
            ProviderOwnerRevocation(
                affectedAccounts = accounts,
                deletedCredentials = credentials,
                deletedPlaybackSessions = sessions,
            )
        }
    }
}

internal data class ProviderOwnerRevocation(
    val affectedAccounts: Int,
    val deletedCredentials: Int,
    val deletedPlaybackSessions: Int,
)
