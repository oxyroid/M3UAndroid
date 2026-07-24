package com.m3u.data.codec

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import java.io.File
import timber.log.Timber

object CodecNativeLoader {
    private val timber = Timber.tag("CodecNativeLoader")
    private val loadedLibraries = mutableSetOf<String>()

    @Volatile
    private var applicationContext: Context? = null

    @JvmStatic
    fun initialize(context: Context) {
        if (!CodecPackConfig.enabled) return
        applicationContext = context.applicationContext
    }

    @JvmStatic
    fun loadLibrary(name: String) {
        synchronized(loadedLibraries) {
            if (name in loadedLibraries) return
        }

        if (loadExternalLibrary(name)) return

        System.loadLibrary(name)
        synchronized(loadedLibraries) { loadedLibraries += name }
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private fun loadExternalLibrary(name: String): Boolean {
        if (!CodecPackConfig.enabled) return false
        val context = applicationContext ?: return false
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        val directory = File(context.noBackupFilesDir, "${CodecPackConfig.DIRECTORY}/${CodecPackConfig.packId}/$abi")
        val library = File(directory, "lib$name.so")
        if (!library.isFile) return false

        val manifest = installedManifest(context) ?: return false
        if (manifest.schemaVersion != SUPPORTED_MANIFEST_SCHEMA) return false
        if (manifest.packId != CodecPackConfig.packId) return false
        val asset = manifest.assets[abi] ?: return false
        val loadOrder = manifest.loadOrder.takeIf { libraries -> name in libraries }.orEmpty()
        val libraries = (loadOrder.ifEmpty { listOf(name) }).map { libraryName ->
            File(directory, "lib$libraryName.so")
        }
        if (libraries.any { file ->
                val expectedSha256 = CodecPackConfig.expectedLibrarySha256(
                    asset.fileName,
                    file.name
                )
                expectedSha256 == null ||
                    !file.isFile ||
                    !CodecPackIntegrity.matchesSha256(file, expectedSha256)
            }
        ) {
            timber.w("refused untrusted external codec library, name=$name, abi=$abi")
            return false
        }

        return runCatching {
            libraries.forEach { file ->
                val libraryName = file.name.removePrefix("lib").removeSuffix(".so")
                synchronized(loadedLibraries) {
                    if (libraryName in loadedLibraries) return@forEach
                }
                System.load(file.absolutePath)
                synchronized(loadedLibraries) { loadedLibraries += libraryName }
            }
        }.onSuccess {
            timber.d("loaded external codec library, name=$name, abi=$abi")
        }.onFailure { error ->
            timber.w(error, "failed to load external codec library, name=$name, abi=$abi")
        }.isSuccess
    }

    private fun installedManifest(context: Context): CodecPackManifest? {
        val manifest = File(context.noBackupFilesDir, "${CodecPackConfig.DIRECTORY}/${CodecPackConfig.packId}/manifest.json")
        if (!manifest.isFile) return null
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString<CodecPackManifest>(manifest.readText())
        }.getOrElse { error ->
            timber.w(error, "failed to read codec pack manifest")
            null
        }
    }

    private const val SUPPORTED_MANIFEST_SCHEMA = 1
}
