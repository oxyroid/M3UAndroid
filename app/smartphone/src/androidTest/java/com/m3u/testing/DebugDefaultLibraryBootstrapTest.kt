package com.m3u.testing

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.m3u.data.database.model.DataSource
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.smartphone.DebugExtensionPlatformEntryPoint
import com.m3u.smartphone.startup.DebugDefaultLibraryWorker
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DebugDefaultLibraryBootstrapTest {
    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext
    private val playlistRepository: PlaylistRepository by lazy {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            DebugExtensionPlatformEntryPoint::class.java,
        ).playlistRepository()
    }

    @Test
    fun freshDebugInstallImportsTheBundledPlaybackSamplesOnce() {
        val deadline = SystemClock.uptimeMillis() + IMPORT_TIMEOUT_MILLIS
        var importedPlaylistUrl: String? = null
        do {
            importedPlaylistUrl = runBlocking {
                playlistRepository.getAll()
                    .singleOrNull { playlist ->
                        playlist.title == DEFAULT_LIBRARY_TITLE
                    }
                    ?.url
            }
            if (importedPlaylistUrl != null) break
            SystemClock.sleep(IMPORT_POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)

        assertNotNull(
            "The smartphone debug build did not import its bundled playback samples",
            importedPlaylistUrl,
        )
        assertEquals(
            "The debug bootstrap must remain idempotent across process restarts",
            1,
            runBlocking {
                playlistRepository.getAll().count { playlist ->
                    playlist.title == DEFAULT_LIBRARY_TITLE
                }
            },
        )
        val imported = runBlocking {
            playlistRepository.getPlaylistWithChannels(
                checkNotNull(importedPlaylistUrl)
            )
        }
        assertNotNull(imported)
        assertEquals(DataSource.M3U, imported?.playlist?.source)
        assertEquals(
            EXPECTED_CHANNEL_IDS,
            imported?.channels
                ?.mapNotNullTo(mutableSetOf()) { channel -> channel.relationId },
        )
        assertEquals(EXPECTED_CHANNEL_IDS.size, imported?.channels?.size)

        val stateFile = context.noBackupFilesDir.resolve(
            "debug-default-library/bootstrap-state-v1"
        )
        assertTrue("The debug bootstrap state was not committed", stateFile.isFile)
        val state = JSONObject(stateFile.readText())
        val manifest = context.assets.open(
            "default-library/manifest.json"
        ).bufferedReader().use { reader ->
            JSONObject(reader.readText())
        }
        assertEquals(1, state.getInt("schemaVersion"))
        assertEquals("imported", state.getString("status"))
        assertEquals(manifest.getString("revision"), state.getString("revision"))
        assertEquals(importedPlaylistUrl, state.getString("playlistUrl"))

        waitForBootstrapWorkToFinish()
        state.put("revision", "outdated-fixture")
        stateFile.writeText(state.toString())
        DebugDefaultLibraryWorker.enqueue(WorkManager.getInstance(context))
        waitForStateRevision(
            stateFile = stateFile,
            expectedRevision = manifest.getString("revision"),
        )
        assertEquals(
            "Updating bundled assets must refresh the tracked playlist in place",
            1,
            runBlocking {
                playlistRepository.getAll().count { playlist ->
                    playlist.title == DEFAULT_LIBRARY_TITLE
                }
            },
        )
    }

    private fun waitForBootstrapWorkToFinish() {
        val workManager = WorkManager.getInstance(context)
        val deadline = SystemClock.uptimeMillis() + IMPORT_TIMEOUT_MILLIS
        do {
            val work = workManager.getWorkInfosForUniqueWork(
                DEFAULT_LIBRARY_WORK_NAME
            ).get(5, TimeUnit.SECONDS)
            if (work.isNotEmpty() && work.all { info -> info.state.isFinished }) {
                return
            }
            SystemClock.sleep(IMPORT_POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        error("The bundled default library bootstrap work did not finish")
    }

    private fun waitForStateRevision(
        stateFile: File,
        expectedRevision: String,
    ) {
        val deadline = SystemClock.uptimeMillis() + IMPORT_TIMEOUT_MILLIS
        do {
            val revision = runCatching {
                JSONObject(stateFile.readText()).getString("revision")
            }.getOrNull()
            if (revision == expectedRevision) return
            SystemClock.sleep(IMPORT_POLL_MILLIS)
        } while (SystemClock.uptimeMillis() < deadline)
        error("The bundled default library did not apply its updated revision")
    }

    private companion object {
        const val DEFAULT_LIBRARY_TITLE = "Debug playback samples"
        const val DEFAULT_LIBRARY_WORK_NAME = "debug-default-library-bootstrap"
        const val IMPORT_TIMEOUT_MILLIS = 20_000L
        const val IMPORT_POLL_MILLIS = 100L
        val EXPECTED_CHANNEL_IDS = setOf(
            "apple.bipbop.avc",
            "blender.big-buck-bunny.hls",
            "blender.sintel.trailer",
        )
    }
}
