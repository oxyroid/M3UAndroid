package com.m3u.extension.transport.android

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
            InstalledExtensionService(
                packageName = service.packageName,
                serviceName = service.name,
                certificateSha256 = packageCertificateSha256(service.packageName),
            )
        }.sortedWith(compareBy(InstalledExtensionService::packageName, InstalledExtensionService::serviceName))
    }

    private fun packageCertificateSha256(packageName: String): String {
        val packageManager = context.packageManager
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        val certificate = packageInfo.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            ?: error("Extension package has no signing certificate")
        return MessageDigest.getInstance("SHA-256")
            .digest(certificate)
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
