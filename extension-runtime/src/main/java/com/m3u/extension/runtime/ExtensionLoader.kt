package com.m3u.extension.runtime

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.m3u.extension.api.tool.JsonHolder
import com.m3u.extension.api.tool.Logger
import com.m3u.extension.api.tool.OkhttpClientHolder
import com.m3u.extension.api.tool.Saver
import com.m3u.extension.api.workflow.Workflow
import dalvik.system.DexClassLoader
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility

class ExtensionLoader @Inject constructor(
    private val json: Json,
    private val logger: Logger,
    private val saver: Saver
) {
    suspend fun loadExtensions(
        context: Context
    ): List<Extension> = coroutineScope {
        loadExtensionPackages(context)
            .map { loadExtension(context, it) }
            .toList()
            .filterNotNull()
    }

    fun loadExtensionPackages(context: Context): Flow<ApplicationInfo> = flow {
        context.packageManager
            .getInstalledPackages(PACKAGE_FLAGS)
            .asSequence()
            .filter { it.isExtension }
            .distinctBy { it.packageName }
            .map { it.applicationInfo }
            .forEach { emit(it) }
    }

    suspend fun loadExtensionFromPkgName(
        context: Context,
        pkgName: String
    ): Extension? = getExtensionInfoFromPkgName(context, pkgName)?.let {
        loadExtension(context, it.applicationInfo)
    }

    suspend fun loadWorkflow(
        context: Context,
        pkgName: String,
        classPath: String
    ): Workflow? = coroutineScope {
        val packageInfo = getExtensionInfoFromPkgName(context, pkgName)?: return@coroutineScope null
        val info = packageInfo.applicationInfo
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
        val sourceClass = classPath.trim()
        val fullPath = if (sourceClass.startsWith(".")) {
            pkgName + sourceClass
        } else {
            sourceClass
        }
        try {
            classLoader
                .loadClass(fullPath)
                .kotlin
                .createInstance()
        } catch (e: Throwable) {
            Log.e(TAG, "loadWorkflow throw an error", e)
            null
        } as? Workflow
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
                    pkgName + sourceClass
                } else {
                    sourceClass
                }
            }
            .map {
                async {
                    try {
                        classLoader
                            .loadClass(it)
                            .kotlin
                            .createInstance()
                    } catch (e: Throwable) {
                        Log.e(TAG, "createInstance throw an error", e)
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
            hlsPropAnalyzer = instances.ensureOneInstanceAtMost(),
            workflows = instances.filterIsInstance<Workflow>()
        )
    }

    private fun <T : Any> KClass<T>.createInstance(): T? {
        val kClass = this
        if (kClass.qualifiedName == null) {
            Log.e(
                TAG,
                "cannot create instance because the class is local" +
                        " or a class of an anonymous object."
            )
            return null
        }
//        if (kClass.hasAnnotation<Sample>()) return null
        val constructor = kClass.constructors
            .asSequence()
            .filter { it.visibility == KVisibility.PUBLIC }
            .minByOrNull { it.parameters.size }
            .also { Log.d(TAG, "detected constructor: $it") }
            ?: return null
        val classifiers = constructor.parameters.map { it.type.classifier }
        Log.d(TAG, "classifiers: $classifiers")
        // todo: not only workflow allowed types.
        if (classifiers.any { it !in Workflow.AllowedType.classifiers }) {
            val notAllowed = classifiers - Workflow.AllowedType.classifiers.toSet()
            Log.w(TAG, "$notAllowed classifiers are not be allowed.")
            return null
        }
        val args = classifiers
            .mapNotNull {
                when (it) {
                    Workflow.AllowedType.OKHTTP_CLIENT_HOLDER.classifier -> OkhttpClientHolder(OkHttpClient())
                    Workflow.AllowedType.JSON_HOLDER.classifier -> JsonHolder(json)
                    Workflow.AllowedType.SAVER.classifier -> saver
                    Workflow.AllowedType.LOGGER.classifier -> logger
                    else -> {
                        Log.e(TAG, "oops.. this log shouldn't be printed?!")
                        null
                    }
                }
            }
        Log.d(TAG, "prepared args: $args")
        return constructor.call(*args.toTypedArray()).also { Log.d(TAG, "createInstance: $it") }
    }

    companion object {
        @Suppress("DEPRECATION")
        private val PACKAGE_FLAGS = PackageManager.GET_CONFIGURATIONS or
                PackageManager.GET_META_DATA or
                PackageManager.GET_SIGNATURES or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else 0)

        private const val FEATURE_EXTENSION = "m3u-android.extension"
        private const val METADATA_EXTENSION_CLASS = "m3u-android.extension.class"
        private const val TAG = "ExtensionLoader"
    }

    private val PackageInfo.isExtension: Boolean
        get() = this.reqFeatures.orEmpty().any { it.name == FEATURE_EXTENSION }

    private inline fun <reified T : Any> List<Any>.ensureOneInstanceAtMost(): T? {
        val instances = filterIsInstance<T>()
        if (instances.size > 1) {
            Log.e(TAG, "package contains more than one same extension instances")
        }
        return instances.firstOrNull()
    }
}