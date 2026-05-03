package com.m3u.data.codec

import android.content.Context
import android.os.Build
import com.m3u.data.api.OkhttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import timber.log.Timber

@Singleton
class CodecPackRepository @Inject constructor(
    @ApplicationContext context: Context,
    @param:OkhttpClient(false) private val okHttpClient: okhttp3.OkHttpClient
) {
    private val applicationContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val timber = Timber.tag("CodecPackRepository")

    val packId: String = CodecPackConfig.packId
    val enabled: Boolean = CodecPackConfig.enabled

    val currentAbi: String? get() = Build.SUPPORTED_ABIS.firstOrNull()

    fun isInstalled(): Boolean {
        if (!CodecPackConfig.enabled) return false
        val manifest = readInstalledManifest() ?: return false
        val asset = selectAsset(manifest) ?: return false
        return isAssetInstalled(manifest, asset)
    }

    fun deleteInstalledPack() {
        File(applicationContext.noBackupFilesDir, "${CodecPackConfig.DIRECTORY}/${CodecPackConfig.packId}")
            .deleteRecursively()
    }

    fun readInstalledManifest(): CodecPackManifest? {
        val file = manifestFile()
        if (!file.isFile) return null
        return runCatching { json.decodeFromString<CodecPackManifest>(file.readText()) }.getOrNull()
    }

    fun installFromDefaultSnapshot(): CodecPackInstallResult {
        if (!CodecPackConfig.enabled) return CodecPackInstallResult.Disabled
        return installFromManifestUrl(CodecPackConfig.defaultManifestUrl())
    }

    fun installFromManifestUrl(manifestUrl: String): CodecPackInstallResult {
        if (!CodecPackConfig.enabled) return CodecPackInstallResult.Disabled
        val manifest = fetchManifest(manifestUrl)
        require(manifest.schemaVersion == 1) { "Unsupported codec manifest schema: ${manifest.schemaVersion}" }
        require(manifest.packId == CodecPackConfig.packId) {
            "Codec pack mismatch: app=${CodecPackConfig.packId}, manifest=${manifest.packId}"
        }

        val asset = selectAsset(manifest)
            ?: return CodecPackInstallResult.UnsupportedAbi(Build.SUPPORTED_ABIS.toList())

        if (isAssetInstalled(manifest, asset)) return CodecPackInstallResult.AlreadyInstalled

        val staging = File(applicationContext.cacheDir, "${CodecPackConfig.DIRECTORY}/${manifest.packId}.download")
            .apply {
                deleteRecursively()
                mkdirs()
            }
        val zipFile = File(staging, asset.fileName)
        download(
            url = CodecPackConfig.assetUrl(asset.path),
            output = zipFile
        )
        require(zipFile.length() == asset.size) {
            "Codec pack size mismatch: expected=${asset.size}, actual=${zipFile.length()}"
        }
        require(md5(zipFile) == asset.md5) { "Codec pack md5 mismatch: ${asset.fileName}" }

        val unpacked = File(staging, "unpacked").apply { mkdirs() }
        unzip(zipFile, unpacked)
        verifyLibraries(unpacked, asset)

        val installDirectory = installDirectory(manifest)
        val installParent = requireNotNull(installDirectory.parentFile) {
            "Codec pack install directory has no parent: ${installDirectory.absolutePath}"
        }.apply { mkdirs() }
        val temporaryInstallDirectory = File(installParent, "${installDirectory.name}.installing").apply {
            deleteRecursively()
        }
        val backupInstallDirectory = File(installParent, "${installDirectory.name}.previous").apply {
            deleteRecursively()
        }

        require(unpacked.renameTo(temporaryInstallDirectory)) {
            "Failed to stage codec pack install: ${temporaryInstallDirectory.absolutePath}"
        }
        val hadInstalledPack = installDirectory.exists()
        if (hadInstalledPack) {
            require(installDirectory.renameTo(backupInstallDirectory)) {
                temporaryInstallDirectory.deleteRecursively()
                "Failed to backup existing codec pack: ${installDirectory.absolutePath}"
            }
        }
        if (!temporaryInstallDirectory.renameTo(installDirectory)) {
            if (hadInstalledPack) backupInstallDirectory.renameTo(installDirectory)
            error("Failed to install codec pack: ${installDirectory.absolutePath}")
        }
        runCatching {
            manifestFile().writeText(json.encodeToString(manifest))
        }.onFailure { error ->
            installDirectory.deleteRecursively()
            if (hadInstalledPack) backupInstallDirectory.renameTo(installDirectory)
            throw error
        }
        backupInstallDirectory.deleteRecursively()
        staging.deleteRecursively()
        timber.d("codec pack installed, pack=${manifest.packId}, abi=${installDirectory.name}")
        return CodecPackInstallResult.Installed
    }

    private fun fetchManifest(url: String): CodecPackManifest {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Failed to fetch codec manifest: ${response.code}" }
            val body = requireNotNull(response.body) { "Codec manifest response body is empty." }
            return json.decodeFromString(body.string())
        }
    }

    private fun download(url: String, output: File) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Failed to download codec pack: ${response.code}" }
            val body = requireNotNull(response.body) { "Codec pack response body is empty." }
            output.outputStream().buffered().use { outputStream -> body.byteStream().use { input -> input.copyTo(outputStream) } }
        }
    }

    private fun unzip(zipFile: File, outputDirectory: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = File(outputDirectory, entry.name)
                require(output.canonicalPath.startsWith(outputDirectory.canonicalPath + File.separator)) {
                    "Unsafe codec pack entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { outputStream -> zip.copyTo(outputStream) }
                }
                zip.closeEntry()
            }
        }
    }

    private fun verifyLibraries(directory: File, asset: CodecPackAsset) {
        asset.libraries.forEach { library ->
            val file = File(directory, library.name)
            require(file.isFile) { "Codec library is missing: ${library.name}" }
            require(file.length() == library.size) {
                "Codec library size mismatch: ${library.name}"
            }
            require(md5(file) == library.md5) {
                "Codec library md5 mismatch: ${library.name}"
            }
        }
    }

    private fun isAssetInstalled(manifest: CodecPackManifest, asset: CodecPackAsset): Boolean {
        val installedManifest = readInstalledManifest() ?: return false
        if (installedManifest.packId != manifest.packId) return false
        val directory = installDirectory(manifest)
        return asset.libraries.all { library ->
            val file = File(directory, library.name)
            file.isFile && file.length() == library.size && md5(file) == library.md5
        }
    }

    private fun selectAsset(manifest: CodecPackManifest): CodecPackAsset? {
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi -> manifest.assets[abi] }
    }

    private fun installDirectory(manifest: CodecPackManifest): File {
        val abi = Build.SUPPORTED_ABIS.first { supportedAbi -> manifest.assets.containsKey(supportedAbi) }
        return File(applicationContext.noBackupFilesDir, "${CodecPackConfig.DIRECTORY}/${manifest.packId}/$abi")
    }

    private fun manifestFile(): File {
        return File(applicationContext.noBackupFilesDir, "${CodecPackConfig.DIRECTORY}/${CodecPackConfig.packId}/manifest.json")
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }
}