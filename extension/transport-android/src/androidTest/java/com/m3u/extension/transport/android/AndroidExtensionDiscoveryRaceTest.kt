package com.m3u.extension.transport.android

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidExtensionDiscoveryRaceTest {
    @Test
    fun packageRemovedAfterQueryIsSkippedWithoutLosingOtherServices() {
        val packageAccess = FakePackageAccess(
            services = listOf(
                serviceInfo("com.example.first", uid = 10_001),
                serviceInfo(REMOVED_PACKAGE, uid = 10_002),
                serviceInfo("com.example.last", uid = 10_003),
            ),
            removedPackages = setOf(REMOVED_PACKAGE),
        )

        val discovered = AndroidExtensionDiscovery.forTesting(packageAccess).discover()

        assertEquals(
            listOf("com.example.first", "com.example.last"),
            discovered.map(InstalledExtensionService::packageName),
        )
    }

    @Test
    fun packageInspectionSecurityFailureIsNotTreatedAsAnUninstallRace() {
        val packageAccess = FakePackageAccess(
            services = listOf(serviceInfo("com.example.denied", uid = 10_001)),
            inspectionFailure = SecurityException("package visibility denied"),
        )

        try {
            AndroidExtensionDiscovery.forTesting(packageAccess).discover()
            fail("Expected the package inspection failure to propagate")
        } catch (expected: SecurityException) {
            assertEquals("package visibility denied", expected.message)
        }
    }

    private class FakePackageAccess(
        private val services: List<ServiceInfo>,
        private val removedPackages: Set<String> = emptySet(),
        private val inspectionFailure: RuntimeException? = null,
    ) : ExtensionDiscoveryPackageAccess {
        override fun queryExtensionServices(): List<ServiceInfo> = services

        override fun getServiceInfo(component: ComponentName): ServiceInfo =
            services.single {
                it.packageName == component.packageName && it.name == component.className
            }

        override fun packageUsesSharedUserId(packageName: String): Boolean {
            inspectionFailure?.let { throw it }
            if (packageName in removedPackages) {
                throw PackageManager.NameNotFoundException(packageName)
            }
            return false
        }

        override fun hasDirectNetworkAccess(uid: Int): Boolean = false

        override fun packageCertificateSha256(packageName: String): String =
            "certificate-$packageName"
    }

    private companion object {
        const val REMOVED_PACKAGE = "com.example.removed"

        fun serviceInfo(
            packageName: String,
            uid: Int,
        ) = ServiceInfo().apply {
            this.packageName = packageName
            name = "$packageName.ExtensionService"
            exported = true
            permission = ExtensionProtocol.HOST_BIND_PERMISSION
            applicationInfo = ApplicationInfo().apply {
                this.uid = uid
            }
        }
    }
}
