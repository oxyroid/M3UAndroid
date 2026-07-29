package com.m3u.smartphone.startup

import android.content.Context
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithChannels
import com.m3u.data.repository.playlist.PlaylistRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@HiltWorker
internal class DebugDefaultLibraryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        when (readBootstrapState()) {
            BootstrapState.IMPORTED,
            BootstrapState.OPTED_OUT -> Result.success()
            BootstrapState.PENDING -> resumePendingImport()
            null -> beginFirstImport()
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: DebugDefaultLibraryFormatException) {
        Result.failure()
    } catch (_: Exception) {
        if (runAttemptCount < MAXIMUM_RETRY_COUNT) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private suspend fun beginFirstImport(): Result {
        if (playlistRepository.getAll().isNotEmpty()) {
            writeBootstrapState(BootstrapState.OPTED_OUT)
            return Result.success()
        }
        writeBootstrapState(BootstrapState.PENDING)
        return resumePendingImport()
    }

    private suspend fun resumePendingImport(): Result {
        val manifest = loadManifest()
        val currentPlaylists = playlistRepository.getAll()
        if (currentPlaylists.isNotEmpty()) {
            writeBootstrapState(
                if (currentPlaylists.containsDefaultLibrary(manifest)) {
                    BootstrapState.IMPORTED
                } else {
                    BootstrapState.OPTED_OUT
                }
            )
            return Result.success()
        }

        val playlistFile = installPlaylistAsset(manifest)
        if (playlistRepository.getAll().isNotEmpty()) {
            writeBootstrapState(BootstrapState.OPTED_OUT)
            return Result.success()
        }
        val playlistUri = FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.provider",
            playlistFile,
        )
        playlistRepository.m3uOrThrow(
            title = manifest.title,
            url = playlistUri.toString(),
        )
        writeBootstrapState(BootstrapState.IMPORTED)
        return Result.success()
    }

    private suspend fun List<Playlist>.containsDefaultLibrary(
        manifest: DebugDefaultLibraryManifest,
    ): Boolean = any { playlist ->
        if (playlist.title != manifest.title) {
            false
        } else {
            playlistRepository.getPlaylistWithChannels(playlist.url)
                ?.matches(manifest)
                ?: false
        }
    }

    private fun PlaylistWithChannels.matches(
        manifest: DebugDefaultLibraryManifest,
    ): Boolean = channels.size == manifest.expectedChannelIds.size &&
        channels.mapNotNullTo(mutableSetOf()) { channel ->
            channel.relationId
        } == manifest.expectedChannelIds

    private suspend fun loadManifest(): DebugDefaultLibraryManifest =
        withContext(Dispatchers.IO) {
            val bytes = applicationContext.assets.open(MANIFEST_ASSET).use { input ->
                input.readBoundedBytes(MAXIMUM_MANIFEST_BYTES)
            }
            DebugDefaultLibraryManifestParser.parse(bytes.decodeToString())
        }

    private suspend fun installPlaylistAsset(
        manifest: DebugDefaultLibraryManifest,
    ): File = withContext(Dispatchers.IO) {
        val bytes = applicationContext.assets.open(manifest.playlistAsset).use { input ->
            input.readBoundedBytes(MAXIMUM_PLAYLIST_BYTES)
        }
        val actualSha256 = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
        if (actualSha256 != manifest.playlistSha256) {
            throw DebugDefaultLibraryFormatException(
                "The bundled playlist SHA-256 does not match its manifest"
            )
        }
        DebugDefaultLibraryManifestParser.validatePlaylist(
            rawPlaylist = bytes.decodeToString(),
            manifest = manifest,
        )
        val directory = File(applicationContext.cacheDir, CACHE_DIRECTORY)
        check(directory.exists() || directory.mkdirs()) {
            "Unable to create the bundled default library cache"
        }
        val destination = File(directory, CACHE_PLAYLIST_NAME)
        val temporary = File(directory, "$CACHE_PLAYLIST_NAME.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        temporary.moveReplacing(destination)
        destination
    }

    private suspend fun readBootstrapState(): BootstrapState? =
        withContext(Dispatchers.IO) {
            val stateFile = bootstrapStateFile()
            if (!stateFile.exists()) {
                null
            } else {
                BootstrapState.entries.singleOrNull { state ->
                    state.serializedValue == stateFile.readText().trim()
                } ?: BootstrapState.OPTED_OUT
            }
        }

    private suspend fun writeBootstrapState(state: BootstrapState) {
        withContext(NonCancellable + Dispatchers.IO) {
            val destination = bootstrapStateFile()
            val directory = checkNotNull(destination.parentFile)
            check(directory.exists() || directory.mkdirs()) {
                "Unable to create the bundled default library state directory"
            }
            val temporary = File(directory, "${destination.name}.tmp")
            FileOutputStream(temporary).use { output ->
                output.write(state.serializedValue.toByteArray())
                output.fd.sync()
            }
            temporary.moveReplacing(destination)
        }
    }

    private fun bootstrapStateFile(): File = File(
        applicationContext.noBackupFilesDir,
        "$STATE_DIRECTORY/$STATE_FILE_NAME",
    )

    private fun File.moveReplacing(destination: File) {
        try {
            Files.move(
                toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                toPath(),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun InputStream.readBoundedBytes(maximumBytes: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(maximumBytes, ASSET_COPY_BUFFER_BYTES))
        val buffer = ByteArray(ASSET_COPY_BUFFER_BYTES)
        var totalBytes = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            if (count == 0) continue
            if (totalBytes > maximumBytes - count) {
                throw DebugDefaultLibraryFormatException(
                    "A bundled default library asset exceeds its size limit"
                )
            }
            output.write(buffer, 0, count)
            totalBytes += count
        }
        return output.toByteArray()
    }

    private enum class BootstrapState(val serializedValue: String) {
        PENDING("pending"),
        IMPORTED("imported"),
        OPTED_OUT("opted-out"),
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "debug-default-library-bootstrap"
        private const val MANIFEST_ASSET = "default-library/manifest.json"
        private const val CACHE_DIRECTORY = "debug-default-library"
        private const val CACHE_PLAYLIST_NAME = "playlist.m3u"
        private const val STATE_DIRECTORY = "debug-default-library"
        private const val STATE_FILE_NAME = "bootstrap-state-v1"
        private const val MAXIMUM_MANIFEST_BYTES = 64 * 1024
        private const val MAXIMUM_PLAYLIST_BYTES = 512 * 1024
        private const val ASSET_COPY_BUFFER_BYTES = 8 * 1024
        private const val MAXIMUM_RETRY_COUNT = 2

        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DebugDefaultLibraryWorker>().build(),
            )
        }
    }
}
