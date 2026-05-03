package com.m3u.data.codec

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

    private fun loadExternalLibrary(name: String): Boolean {
        if (!CodecPackConfig.enabled) return false
        val context = applicationContext ?: return false
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return false
        val directory = File(context.noBackupFilesDir, "${CodecPackConfig.DIRECTORY}/${CodecPackConfig.packId}/$abi")
        val library = File(directory, "lib$name.so")
        if (!library.isFile) return false

        val loadOrder = loadOrder(context).takeIf { libraries -> name in libraries }.orEmpty()
        val libraries = (loadOrder.ifEmpty { listOf(name) }).map { libraryName ->
            File(directory, "lib$libraryName.so")
        }
        if (libraries.any { file -> !file.isFile }) return false

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

    private fun loadOrder(context: Context): List<String> {
        val manifest = File(context.noBackupFilesDir, "${CodecPackConfig.DIRECTORY}/${CodecPackConfig.packId}/manifest.json")
        if (!manifest.isFile) return emptyList()
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString<CodecPackManifest>(manifest.readText()).loadOrder
        }.getOrElse { error ->
            timber.w(error, "failed to read codec pack manifest")
            emptyList()
        }
    }
}