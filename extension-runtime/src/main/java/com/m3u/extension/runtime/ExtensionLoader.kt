package com.m3u.extension.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dalvik.system.DexClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal object ExtensionLoader {
    suspend fun loadExtensions(
        context: Context
    ): List<Extension> = coroutineScope {
        loadExtensionPackages(context)
            .map { async { loadExtension(context, it) } }
            .toList()
            .awaitAll()
            .filterNotNull()
    }

    suspend fun loadExtensionPackages(
        context: Context
    ): Sequence<ApplicationInfo> = coroutineScope {
        context.packageManager
            .getInstalledPackages(PACKAGE_FLAGS)
            .asSequence()
            .filter { it.isExtension }
            .distinctBy { it.packageName }
            .map { it.applicationInfo }
    }

    suspend fun loadExtensionFromPkgName(
        context: Context,
        pkgName: String
    ): Extension? = getExtensionInfoFromPkgName(context, pkgName)?.let {
        loadExtension(context, it.applicationInfo)
    }

    private fun getExtensionInfoFromPkgName(context: Context, pkgName: String): PackageInfo? {
        val packageInfo = try {
            context.packageManager.getPackageInfo(pkgName, PACKAGE_FLAGS).takeIf { it.isExtension }
        } catch (error: PackageManager.NameNotFoundException) {
            null
        }
        return packageInfo
    }

    suspend fun loadExtension(
        context: Context,
        info: ApplicationInfo
    ): Extension? = coroutineScope {
        val pkgName = info.packageName
        val classLoader = try {
            DexClassLoader(
                info.sourceDir,
                context.codeCacheDir.absolutePath,
                info.nativeLibraryDir,
                context.classLoader
            )
        } catch (e: Exception) {
            return@coroutineScope null
        }
        val instances = info.metaData.getString(METADATA_EXTENSION_CLASS).orEmpty()
            .split(";")
            .map {
                val sourceClass = it.trim()
                if (sourceClass.startsWith(".")) {
                    info.packageName + sourceClass
                } else {
                    sourceClass
                }
            }
            .map {
                async {
                    try {
                        classLoader
                            .loadClass(it)
                            .getDeclaredConstructor()
                            .newInstance()
                    } catch (e: Throwable) {
                        null
                    }
                }
            }
            .awaitAll()
            .filterNotNull()

        Extension(
            label = info.loadLabel(context.packageManager).toString(),
            icon = info.loadIcon(context.packageManager),
            packageName = info.packageName,
            hlsPropAnalyzer = instances.ensureOneInstanceAtMost()
        )
    }

    @Suppress("DEPRECATION")
    private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
            PackageManager.GET_META_DATA or
            PackageManager.GET_SIGNATURES or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

    private const val FEATURE_EXTENSION = "m3u-android.extension"
    private const val METADATA_EXTENSION_CLASS = "m3u-android.extension.class"

    private val PackageInfo.isExtension: Boolean
        get() = this.reqFeatures.orEmpty().any { it.name == FEATURE_EXTENSION }

    private inline fun <reified T : Any> List<Any>.ensureOneInstanceAtMost(): T? {
        val instances = filterIsInstance<T>()
        if (instances.size > 1) {
            Log.e("ExtensionLoader", "package contains more than one same extension instances")
        }
        return instances.firstOrNull()
    }
}