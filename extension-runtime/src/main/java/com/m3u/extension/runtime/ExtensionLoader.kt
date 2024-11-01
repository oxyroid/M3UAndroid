package com.m3u.extension.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.m3u.extension.api.analyzer.Analyzer
import com.m3u.extension.api.runner.Runner
import dalvik.system.DexClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object ExtensionLoader {
    suspend fun loadExtensions(context: Context): List<Extension> = coroutineScope {
        context.packageManager
            .getInstalledPackages(PACKAGE_FLAGS)
            .asSequence()
            .filter { it.isExtension }
            .distinctBy { it.packageName }
            .map { async { loadExtension(context, it.applicationInfo) } }
            .toList()
            .awaitAll()
            .filterNotNull()
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

        val runners = instances.filterIsInstance<Runner>()
        val analyzers = instances.filterIsInstance<Analyzer>().sortedByDescending { it.priority }

        Extension(
            packageName = pkgName,
            runners = runners,
            analyzers = analyzers
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
}