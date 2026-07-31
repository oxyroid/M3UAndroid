package com.m3u.smartphone.startup

import android.content.Context
import androidx.core.content.FileProvider
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
import timber.log.Timber

@HiltWorker
internal class DebugDefaultLibraryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val playlistRepository: PlaylistRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val manifest = loadManifest()
        val state = readBootstrapState()
        when (state?.status) {
            DebugDefaultLibraryBootstrapStatus.OPTED_OUT -> Result.success()
            DebugDefaultLibraryBootstrapStatus.IMPORTED -> {
                if (
                    state.needsAssetUpdate(
                        targetRevision = manifest.revision,
                        targetPlaylistSha256 = manifest.playlistSha256,
                    )
                ) {
                    updateImportedLibrary(state, manifest)
                } else {
                    Result.success()
                }
            }
            DebugDefaultLibraryBootstrapStatus.PENDING ->
                resumePendingImport(manifest)
            null -> beginFirstImport(manifest)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: DebugDefaultLibraryFormatException) {
        failureResult(FAILURE_KIND_INVALID_ASSET, error)
    } catch (error: Exception) {
        Timber.tag(LOG_TAG).e(
            error,
            "Bundled default library import failed on attempt %d",
            runAttemptCount + 1,
        )
        if (runAttemptCount < MAXIMUM_RETRY_COUNT) {
            Result.retry()
        } else {
            failureResult(FAILURE_KIND_IMPORT, error)
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
                playlistSha256 = manifest.playlistSha256,
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
        val tracked = selectTrackedDefaultLibraryPlaylist(
            state = state,
            currentPlaylists = currentPlaylists,
        )
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
            playlistSha256 = manifest.playlistSha256,
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

    private fun failureResult(
        failureKind: String,
        error: Exception,
    ): Result {
        Timber.tag(LOG_TAG).e(error, "Bundled default library import rejected")
        val diagnostic = error.message
            ?.take(MAXIMUM_DIAGNOSTIC_CHARACTERS)
            ?.takeIf(String::isNotBlank)
            ?: error::class.java.simpleName
        return Result.failure(
            workDataOf(
                OUTPUT_FAILURE_KIND to failureKind,
                OUTPUT_FAILURE_DIAGNOSTIC to diagnostic,
            )
        )
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
        private const val MAXIMUM_DIAGNOSTIC_CHARACTERS = 512
        private const val LOG_TAG = "DefaultLibraryBootstrap"
        internal const val OUTPUT_FAILURE_KIND =
            "default_library_failure_kind"
        internal const val OUTPUT_FAILURE_DIAGNOSTIC =
            "default_library_failure_diagnostic"
        internal const val FAILURE_KIND_INVALID_ASSET = "invalid-asset"
        internal const val FAILURE_KIND_IMPORT = "import-failed"

        internal fun hasSettled(context: Context): Boolean {
            val stateFile = File(
                context.noBackupFilesDir,
                "$STATE_DIRECTORY/$STATE_FILE_NAME",
            )
            val status = runCatching {
                DebugDefaultLibraryBootstrapStateCodec
                    .decodeOrNull(stateFile.readText())
                    ?.status
            }.getOrNull()
            return status == DebugDefaultLibraryBootstrapStatus.IMPORTED ||
                status == DebugDefaultLibraryBootstrapStatus.OPTED_OUT
        }

        fun enqueue(workManager: WorkManager) {
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DebugDefaultLibraryWorker>().build(),
            )
        }
    }
}

internal fun selectTrackedDefaultLibraryPlaylist(
    state: DebugDefaultLibraryBootstrapState,
    currentPlaylists: List<Playlist>,
): Playlist? = state.playlistUrl?.let { playlistUrl ->
    currentPlaylists.singleOrNull { playlist ->
        playlist.url == playlistUrl
    }
}
