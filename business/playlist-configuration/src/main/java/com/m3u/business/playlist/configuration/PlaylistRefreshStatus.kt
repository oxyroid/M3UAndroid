package com.m3u.business.playlist.configuration

import androidx.compose.runtime.Immutable
import androidx.work.WorkInfo

@Immutable
enum class PlaylistRefreshStatus {
    IDLE,
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    val isInProgress: Boolean
        get() = this == ENQUEUED || this == RUNNING
}

internal enum class RefreshLaunchPhase {
    IDLE,
    ENQUEUING,
    WORK,
    FAILED,
}

internal fun resolvePlaylistRefreshStatus(
    launchPhase: RefreshLaunchPhase,
    trackedWorkState: WorkInfo.State?,
    activeWorkState: WorkInfo.State?,
): PlaylistRefreshStatus = when (launchPhase) {
    RefreshLaunchPhase.ENQUEUING -> PlaylistRefreshStatus.ENQUEUED
    RefreshLaunchPhase.FAILED -> PlaylistRefreshStatus.FAILED
    RefreshLaunchPhase.WORK ->
        trackedWorkState?.toPlaylistRefreshStatus()
            ?: PlaylistRefreshStatus.ENQUEUED
    RefreshLaunchPhase.IDLE ->
        activeWorkState
            ?.takeUnless { state -> state.isFinished }
            ?.toPlaylistRefreshStatus()
            ?: PlaylistRefreshStatus.IDLE
}

private fun WorkInfo.State.toPlaylistRefreshStatus(): PlaylistRefreshStatus = when (this) {
    WorkInfo.State.BLOCKED,
    WorkInfo.State.ENQUEUED -> PlaylistRefreshStatus.ENQUEUED
    WorkInfo.State.RUNNING -> PlaylistRefreshStatus.RUNNING
    WorkInfo.State.SUCCEEDED -> PlaylistRefreshStatus.SUCCEEDED
    WorkInfo.State.FAILED -> PlaylistRefreshStatus.FAILED
    WorkInfo.State.CANCELLED -> PlaylistRefreshStatus.CANCELLED
}
