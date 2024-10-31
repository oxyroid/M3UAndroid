package com.m3u.extension.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.m3u.extension.api.runner.Runner
import com.m3u.extension.runtime.internal.ChildFirstPathClassLoader
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

    private suspend fun loadExtension(context: Context, info: ApplicationInfo): Extension? {
        val pkgName = info.packageName

        val classLoader = try {
            val dexInternalStoragePath = context.getDir("dex", Context.MODE_PRIVATE)
            DexClassLoader(
                info.sourceDir,
                context.codeCacheDir.absolutePath,
                info.nativeLibraryDir,
                context.classLoader
            )
        } catch (e: Exception) {
            return null
        }

        val runners = info.metaData.getString(METADATA_EXTENSION_CLASS)
            .orEmpty()
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
                try {
                    classLoader
                        .loadClass(it)
                        .getDeclaredConstructor()
                        .newInstance()
                } catch (e: Throwable) {
                    Log.e("TAG", "loadExtension: ", e)
                    return null
                }
            }
            .onEach {
                Log.e("TAG", "loadExtension: $it", )
            }
            .filterIsInstance<Runner>()

        return Extension(
            packageName = pkgName,
            runners = runners
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