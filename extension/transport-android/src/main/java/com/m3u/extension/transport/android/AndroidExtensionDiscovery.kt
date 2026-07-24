package com.m3u.extension.transport.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

class AndroidExtensionDiscovery private constructor(
    private val packageAccess: ExtensionDiscoveryPackageAccess,
) {
    constructor(context: Context) : this(AndroidExtensionDiscoveryPackageAccess(context))

    fun discover(): List<InstalledExtensionService> =
        packageAccess.queryExtensionServices().mapNotNull { service ->
            try {
                service.toInstalledExtensionService()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedWith(compareBy(InstalledExtensionService::packageName, InstalledExtensionService::serviceName))

    fun resolve(component: ComponentName): InstalledExtensionService? = try {
        val service = packageAccess.getServiceInfo(component)
        if (service.packageName != component.packageName || service.name != component.className) {
            null
        } else {
            service.toInstalledExtensionService()
        }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun ServiceInfo.toInstalledExtensionService(): InstalledExtensionService? {
        if (!exported || permission != ExtensionProtocol.HOST_BIND_PERMISSION) return null
        val processIdentityIncompatible =
            flags and ServiceInfo.FLAG_ISOLATED_PROCESS != 0 ||
                flags and ServiceInfo.FLAG_EXTERNAL_SERVICE != 0
        val uid = applicationInfo?.uid ?: return null
        val usesSharedUserId = packageAccess.packageUsesSharedUserId(packageName)
        val hasDirectNetworkAccess = packageAccess.hasDirectNetworkAccess(uid)
        val incompatibilityReason = extensionIdentityIncompatibilityReason(
            processIdentityIncompatible = processIdentityIncompatible,
            usesSharedUserId = usesSharedUserId,
            hasDirectNetworkAccess = hasDirectNetworkAccess,
        )
        return InstalledExtensionService(
            packageName = packageName,
            serviceName = name,
            certificateSha256 = packageAccess.packageCertificateSha256(packageName),
            uid = uid,
            incompatibilityReason = incompatibilityReason,
        )
    }

    companion object {
        internal fun forTesting(packageAccess: ExtensionDiscoveryPackageAccess) =
            AndroidExtensionDiscovery(packageAccess)
    }
}

internal interface ExtensionDiscoveryPackageAccess {
    fun queryExtensionServices(): List<ServiceInfo>

    @Throws(PackageManager.NameNotFoundException::class)
    fun getServiceInfo(component: ComponentName): ServiceInfo

    @Throws(PackageManager.NameNotFoundException::class)
    fun packageUsesSharedUserId(packageName: String): Boolean

    fun hasDirectNetworkAccess(uid: Int): Boolean

    @Throws(PackageManager.NameNotFoundException::class)
    fun packageCertificateSha256(packageName: String): String
}

private class AndroidExtensionDiscoveryPackageAccess(
    private val context: Context,
) : ExtensionDiscoveryPackageAccess {
    private val packageManager = context.packageManager

    override fun queryExtensionServices(): List<ServiceInfo> {
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
        return services.mapNotNull { resolveInfo -> resolveInfo.serviceInfo }
    }

    override fun getServiceInfo(component: ComponentName): ServiceInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getServiceInfo(
                component,
                PackageManager.ComponentInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getServiceInfo(component, 0)
        }

    @Suppress("DEPRECATION")
    override fun packageUsesSharedUserId(packageName: String): Boolean {
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            packageManager.getPackageInfo(packageName, 0)
        }
        return packageInfo.sharedUserId != null
    }

    override fun hasDirectNetworkAccess(uid: Int): Boolean =
        context.checkPermission(
            Manifest.permission.INTERNET,
            -1,
            uid,
        ) == PackageManager.PERMISSION_GRANTED

    override fun packageCertificateSha256(packageName: String): String {
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
