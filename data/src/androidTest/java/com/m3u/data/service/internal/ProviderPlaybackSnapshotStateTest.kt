package com.m3u.data.service.internal

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProviderPlaybackSnapshotStateTest {
    @Test
    fun providerReportingReadsThreadSafeSnapshotOffApplicationThread() = runBlocking {
        val state = ProviderPlaybackSnapshotState()
        state.update(
            positionMillis = 12_345L,
            isPaused = false,
        )

        val playingSnapshot = withContext(Dispatchers.IO) {
            state.read()
        }
        assertEquals(
            ProviderPlaybackSnapshot(positionMillis = 12_345L, isPaused = false),
            playingSnapshot,
        )

        state.updatePosition(positionMillis = -1L)
        state.updatePaused(isPaused = true)
        val pausedSnapshot = withContext(Dispatchers.IO) {
            state.read()
        }
        assertEquals(
            ProviderPlaybackSnapshot(positionMillis = 0L, isPaused = true),
            pausedSnapshot,
        )
    }

    @Test
    fun serverResolvedTranscodeOffsetIsAddedToRelativePlayerPosition() {
        val oneHourMillis = 60L * 60L * 1_000L

        assertEquals(
            oneHourMillis + 10_000L,
            10_000L.toAbsolutePlaybackPositionMillis(
                offsetMillis = oneHourMillis,
            ),
        )
        assertEquals(
            -1L,
            (-1L).toAbsolutePlaybackPositionMillis(
                offsetMillis = oneHourMillis,
            ),
        )
        assertEquals(
            Long.MAX_VALUE,
            Long.MAX_VALUE.toAbsolutePlaybackPositionMillis(
                offsetMillis = oneHourMillis,
            ),
        )
    }
}
