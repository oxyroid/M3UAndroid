package com.m3u.business.playlist.configuration

import androidx.work.WorkInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class PlaylistRefreshStatusTest {
    @Test
    fun `tracked work exposes every work manager phase`() {
        val expected = mapOf(
            WorkInfo.State.BLOCKED to PlaylistRefreshStatus.ENQUEUED,
            WorkInfo.State.ENQUEUED to PlaylistRefreshStatus.ENQUEUED,
            WorkInfo.State.RUNNING to PlaylistRefreshStatus.RUNNING,
            WorkInfo.State.SUCCEEDED to PlaylistRefreshStatus.SUCCEEDED,
            WorkInfo.State.FAILED to PlaylistRefreshStatus.FAILED,
            WorkInfo.State.CANCELLED to PlaylistRefreshStatus.CANCELLED,
        )

        expected.forEach { (workState, refreshStatus) ->
            assertEquals(
                refreshStatus,
                resolvePlaylistRefreshStatus(
                    launchPhase = RefreshLaunchPhase.WORK,
                    trackedWorkState = workState,
                    activeWorkState = null,
                ),
            )
        }
    }

    @Test
    fun `cancelled work is not reported as failure`() {
        assertEquals(
            PlaylistRefreshStatus.CANCELLED,
            resolvePlaylistRefreshStatus(
                launchPhase = RefreshLaunchPhase.WORK,
                trackedWorkState = WorkInfo.State.CANCELLED,
                activeWorkState = null,
            ),
        )
    }

    @Test
    fun `terminal history is ignored without a user tracked work`() {
        listOf(
            WorkInfo.State.SUCCEEDED,
            WorkInfo.State.FAILED,
            WorkInfo.State.CANCELLED,
        ).forEach { historicalState ->
            assertEquals(
                PlaylistRefreshStatus.IDLE,
                resolvePlaylistRefreshStatus(
                    launchPhase = RefreshLaunchPhase.IDLE,
                    trackedWorkState = null,
                    activeWorkState = historicalState,
                ),
            )
        }
    }

    @Test
    fun `currently active background work remains visible`() {
        assertEquals(
            PlaylistRefreshStatus.RUNNING,
            resolvePlaylistRefreshStatus(
                launchPhase = RefreshLaunchPhase.IDLE,
                trackedWorkState = null,
                activeWorkState = WorkInfo.State.RUNNING,
            ),
        )
    }

    @Test
    fun `enqueue failure is visible without a work id`() {
        assertEquals(
            PlaylistRefreshStatus.FAILED,
            resolvePlaylistRefreshStatus(
                launchPhase = RefreshLaunchPhase.FAILED,
                trackedWorkState = null,
                activeWorkState = null,
            ),
        )
    }
}
