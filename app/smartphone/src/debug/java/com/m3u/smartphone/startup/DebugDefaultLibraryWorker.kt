package com.m3u.smartphone.startup

import android.content.Context
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.m3u.data.database.model.DataSource
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
        val state = readBootstrapState()
        when (state?.status) {
            DebugDefaultLibraryBootstrapStatus.OPTED_OUT -> Result.success()
            DebugDefaultLibraryBootstrapStatus.IMPORTED -> {
                val manifest = loadManifest()
                if (state.needsAssetUpdate(manifest.revision)) {
                    updateImportedLibrary(state, manifest)
                } else {
                    Result.success()
                }
            }
            DebugDefaultLibraryBootstrapStatus.PENDING ->
                resumePendingImport(loadManifest())
            null -> beginFirstImport(loadManifest())
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

    private suspend fun beginFirstImport(
        manifest: DebugDefaultLibraryManifest,
    ): Result {
        if (playlistRepository.getAll().isNotEmpty()) {
            writeBootstrapState(optedOutState())
            return Result.success()
        }
        writeBootstrapState(
            DebugDefaultLibraryBootstrapState(
                status = DebugDefaultLibraryBootstrapStatus.PENDING,
                revision = manifest.revision,
            )
        )
        return resumePendingImport(manifest)
    }

    private suspend fun resumePendingImport(
        manifest: DebugDefaultLibraryManifest,
    ): Result {
        val currentPlaylists = playlistRepository.getAll()
        if (currentPlaylists.isNotEmpty()) {
            val imported = currentPlaylists.findDefaultLibrary(manifest)
            writeBootstrapState(imported?.toImportedState(manifest) ?: optedOutState())
            return Result.success()
        }

        val playlistFile = installPlaylistAsset(manifest)
        if (playlistRepository.getAll().isNotEmpty()) {
            writeBootstrapState(optedOutState())
            return Result.success()
        }
        playlistRepository.m3uOrThrow(
            title = manifest.title,
            url = playlistFile.contentUri(),
        )
        val imported = playlistRepository.getAll().findDefaultLibrary(manifest)
            ?: throw DebugDefaultLibraryFormatException(
                "The bundled default library import did not commit its expected channels"
            )
        writeBootstrapState(imported.toImportedState(manifest))
        return Result.success()
    }

    private suspend fun updateImportedLibrary(
        state: DebugDefaultLibraryBootstrapState,
        manifest: DebugDefaultLibraryManifest,
    ): Result {
        val currentPlaylists = playlistRepository.getAll()
        val tracked = state.playlistUrl
            ?.let { playlistUrl ->
                currentPlaylists.singleOrNull { playlist ->
                    playlist.url == playlistUrl
                }
            }
            ?: currentPlaylists.singleOrNull { playlist ->
                playlist.source == DataSource.M3U &&
                    playlist.title == manifest.title
            }
        if (tracked == null) {
            writeBootstrapState(optedOutState())
            return Result.success()
        }

        val playlistFile = installPlaylistAsset(manifest)
        playlistRepository.m3uOrThrow(
            title = tracked.title,
            url = playlistFile.contentUri(),
        )
        val updated = playlistRepository.getPlaylistWithChannels(tracked.url)
        if (updated?.matches(manifest) != true) {
            throw DebugDefaultLibraryFormatException(
                "The bundled default library update did not commit its expected channels"
            )
        }
        writeBootstrapState(tracked.toImportedState(manifest))
        return Result.success()
    }

    private suspend fun List<Playlist>.findDefaultLibrary(
        manifest: DebugDefaultLibraryManifest,
    ): Playlist? = firstOrNull { playlist ->
        playlist.source == DataSource.M3U &&
            playlist.title == manifest.title &&
            (
                playlistRepository.getPlaylistWithChannels(playlist.url)
                    ?.matches(manifest) == true
            )
    }

    private fun PlaylistWithChannels.matches(
        manifest: DebugDefaultLibraryManifest,
    ): Boolean = channels.size == manifest.expectedChannelIds.size &&
        channels.mapNotNullTo(mutableSetOf()) { channel ->
            channel.relationId
        } == manifest.expectedChannelIds

    private fun Playlist.toImportedState(
        manifest: DebugDefaultLibraryManifest,
    ): DebugDefaultLibraryBootstrapState =
        DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.IMPORTED,
            revision = manifest.revision,
            playlistUrl = url,
        )

    private fun optedOutState(): DebugDefaultLibraryBootstrapState =
        DebugDefaultLibraryBootstrapState(
            status = DebugDefaultLibraryBootstrapStatus.OPTED_OUT,
        )

    private fun File.contentUri(): String = FileProvider.getUriForFile(
        applicationContext,
        "${applicationContext.packageName}.provider",
        this,
    ).toString()

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

    private suspend fun readBootstrapState(): DebugDefaultLibraryBootstrapState? =
        withContext(Dispatchers.IO) {
            val stateFile = bootstrapStateFile()
            if (!stateFile.exists()) {
                null
            } else {
                DebugDefaultLibraryBootstrapStateCodec.decodeOrNull(
                    stateFile.readText()
                ) ?: optedOutState()
            }
        }

    private suspend fun writeBootstrapState(
        state: DebugDefaultLibraryBootstrapState,
    ) {
        withContext(NonCancellable + Dispatchers.IO) {
            val destination = bootstrapStateFile()
            val directory = checkNotNull(destination.parentFile)
            check(directory.exists() || directory.mkdirs()) {
                "Unable to create the bundled default library state directory"
            }
            val temporary = File(directory, "${destination.name}.tmp")
            FileOutputStream(temporary).use { output ->
                output.write(
                    DebugDefaultLibraryBootstrapStateCodec.encode(state).toByteArray()
                )
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
