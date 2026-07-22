package com.m3u.extension.transport.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

class AndroidExtensionDiscovery(private val context: Context) {
    fun discover(): List<InstalledExtensionService> {
        val packageManager = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 33) {
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        } else {
            @Suppress("DEPRECATION")
            PackageManager.MATCH_ALL
        }
        val services = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentServices(Intent(ExtensionProtocol.SERVICE_ACTION), flags as PackageManager.ResolveInfoFlags)
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentServices(Intent(ExtensionProtocol.SERVICE_ACTION), flags as Int)
        }
        return services.mapNotNull { resolveInfo ->
            val service = resolveInfo.serviceInfo ?: return@mapNotNull null
            if (!service.exported || service.permission != ExtensionProtocol.HOST_BIND_PERMISSION) return@mapNotNull null
            val processIdentityIncompatible =
                service.flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0 ||
                service.flags and ServiceInfo.FLAG_EXTERNAL_SERVICE != 0
            val uid = service.applicationInfo?.uid ?: return@mapNotNull null
            val usesSharedUserId = packageUsesSharedUserId(service.packageName)
            val hasDirectNetworkAccess = context.checkPermission(
                Manifest.permission.INTERNET,
                -1,
                uid,
            ) == PackageManager.PERMISSION_GRANTED
            val incompatibilityReason = extensionIdentityIncompatibilityReason(
                processIdentityIncompatible = processIdentityIncompatible,
                usesSharedUserId = usesSharedUserId,
                hasDirectNetworkAccess = hasDirectNetworkAccess,
            )
            InstalledExtensionService(
                packageName = service.packageName,
                serviceName = service.name,
                certificateSha256 = packageCertificateSha256(service.packageName),
                uid = uid,
                incompatibilityReason = incompatibilityReason,
            )
        }.sortedWith(compareBy(InstalledExtensionService::packageName, InstalledExtensionService::serviceName))
    }

    @Suppress("DEPRECATION")
    private fun packageUsesSharedUserId(packageName: String): Boolean {
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            context.packageManager.getPackageInfo(packageName, 0)
        }
        return packageInfo.sharedUserId != null
    }

    private fun packageCertificateSha256(packageName: String): String {
        val packageManager = context.packageManager
        val certificates = if (Build.VERSION.SDK_INT >= 28) {
            val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
        }
        return certificateSetSha256(certificates.map(Signature::toByteArray))
    }
}

internal fun extensionIdentityIncompatibilityReason(
    processIdentityIncompatible: Boolean,
    usesSharedUserId: Boolean,
    hasDirectNetworkAccess: Boolean,
): String? = when {
    processIdentityIncompatible -> "Extension service process identity is not supported"
    usesSharedUserId -> "Extension package must have its own Android UID"
    hasDirectNetworkAccess -> "Extension package must use the host network broker"
    else -> null
}

internal fun certificateSetSha256(certificates: Iterable<ByteArray>): String {
    val fingerprints = certificates
        .map { certificate -> MessageDigest.getInstance("SHA-256").digest(certificate).toHexString() }
        .toSortedSet()
    check(fingerprints.isNotEmpty()) { "Extension package has no signing certificate" }
    return fingerprints.joinToString(SIGNER_FINGERPRINT_SEPARATOR)
}

private fun ByteArray.toHexString(): String = buildString(size * 2) {
    for (byte in this@toHexString) {
        val value = byte.toInt() and 0xff
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0f])
    }
}

private const val SIGNER_FINGERPRINT_SEPARATOR = ","
private const val HEX_DIGITS = "0123456789abcdef"
