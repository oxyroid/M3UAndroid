package com.m3u.business.setting

import androidx.work.WorkInfo
import com.m3u.data.database.model.DataSource
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaylistSubscriptionStateTest {
    private val workId = UUID.fromString("71f17847-cb07-428d-b390-9b4169de4c22")
    private val tracking = PlaylistSubscriptionTracking(
        title = "Living room",
        source = DataSource.M3U,
        workId = workId,
    )

    @Test
    fun `tracked work exposes every work manager phase`() {
        val expected = mapOf(
            WorkInfo.State.BLOCKED to PlaylistSubscriptionPhase.ENQUEUED,
            WorkInfo.State.ENQUEUED to PlaylistSubscriptionPhase.ENQUEUED,
            WorkInfo.State.RUNNING to PlaylistSubscriptionPhase.RUNNING,
            WorkInfo.State.SUCCEEDED to PlaylistSubscriptionPhase.SUCCEEDED,
            WorkInfo.State.FAILED to PlaylistSubscriptionPhase.FAILED,
            WorkInfo.State.CANCELLED to PlaylistSubscriptionPhase.CANCELLED,
        )

        expected.forEach { (workState, phase) ->
            val state = resolvePlaylistSubscriptionState(tracking, workState)

            assertEquals(phase, state.phase)
            assertEquals("Living room", state.title)
            assertEquals(DataSource.M3U, state.source)
            assertEquals(workId, state.workId)
        }
    }

    @Test
    fun `work not visible yet remains enqueued`() {
        val state = resolvePlaylistSubscriptionState(
            tracking = tracking,
            workState = null,
        )

        assertEquals(PlaylistSubscriptionPhase.ENQUEUED, state.phase)
        assertTrue(state.isInProgress)
        assertFalse(state.isTerminal)
    }

    @Test
    fun `only terminal phases are dismissible feedback`() {
        listOf(
            WorkInfo.State.SUCCEEDED,
            WorkInfo.State.FAILED,
            WorkInfo.State.CANCELLED,
        ).forEach { workState ->
            val state = resolvePlaylistSubscriptionState(tracking, workState)

            assertFalse(state.isInProgress)
            assertTrue(state.isTerminal)
        }
    }

    @Test
    fun `restored context accepts only supported source and valid id`() {
        assertEquals(
            PlaylistSubscriptionTracking(
                title = "News",
                source = DataSource.Xtream,
                workId = workId,
            ),
            restorePlaylistSubscriptionTracking(
                title = " News ",
                sourceValue = DataSource.Xtream.value,
                workIdValue = workId.toString(),
            ),
        )
        assertNull(
            restorePlaylistSubscriptionTracking(
                title = "News",
                sourceValue = DataSource.Provider.value,
                workIdValue = workId.toString(),
            )
        )
        assertNull(
            restorePlaylistSubscriptionTracking(
                title = "News",
                sourceValue = DataSource.M3U.value,
                workIdValue = "not-a-uuid",
            )
        )
    }

    @Test
    fun `restored title is sanitized and bounded`() {
        val restored = restorePlaylistSubscriptionTracking(
            title = "\u202E News\n${"x".repeat(400)} ",
            sourceValue = DataSource.M3U.value,
            workIdValue = workId.toString(),
        )

        requireNotNull(restored)
        assertFalse('\u202E' in restored.title)
        assertFalse('\n' in restored.title)
        assertTrue(restored.title.length <= 256)
    }
}
