package com.m3u.business.setting

import androidx.work.WorkInfo
import com.m3u.core.foundation.util.basic.PlaylistInputKind
import com.m3u.core.foundation.util.basic.normalizePlaylistInputForSubmission
import com.m3u.data.database.model.DataSource
import java.util.UUID

enum class PlaylistSubscriptionPhase {
    IDLE,
    ENQUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class PlaylistSubscriptionState(
    val phase: PlaylistSubscriptionPhase,
    val title: String? = null,
    val source: DataSource? = null,
    val workId: UUID? = null,
) {
    val isInProgress: Boolean
        get() = phase == PlaylistSubscriptionPhase.ENQUEUED ||
            phase == PlaylistSubscriptionPhase.RUNNING

    val isTerminal: Boolean
        get() = phase == PlaylistSubscriptionPhase.SUCCEEDED ||
            phase == PlaylistSubscriptionPhase.FAILED ||
            phase == PlaylistSubscriptionPhase.CANCELLED

    companion object {
        val Idle = PlaylistSubscriptionState(
            phase = PlaylistSubscriptionPhase.IDLE,
        )
    }
}

internal data class PlaylistSubscriptionTracking(
    val title: String,
    val source: DataSource,
    val workId: UUID,
)

internal fun createPlaylistSubscriptionTracking(
    title: String,
    source: DataSource,
    workId: UUID,
): PlaylistSubscriptionTracking? {
    if (source != DataSource.M3U && source != DataSource.Xtream) return null
    val sanitizedTitle = title.normalizePlaylistInputForSubmission(
        PlaylistInputKind.TITLE
    )
    if (sanitizedTitle.isBlank()) return null
    return PlaylistSubscriptionTracking(
        title = sanitizedTitle,
        source = source,
        workId = workId,
    )
}

internal fun restorePlaylistSubscriptionTracking(
    title: String,
    sourceValue: String,
    workIdValue: String,
): PlaylistSubscriptionTracking? {
    val source = DataSource.ofOrNull(sourceValue) ?: return null
    val workId = runCatching { UUID.fromString(workIdValue) }
        .getOrNull()
        ?: return null
    return createPlaylistSubscriptionTracking(
        title = title,
        source = source,
        workId = workId,
    )
}

internal fun resolvePlaylistSubscriptionState(
    tracking: PlaylistSubscriptionTracking,
    workState: WorkInfo.State?,
): PlaylistSubscriptionState = PlaylistSubscriptionState(
    phase = when (workState) {
        null,
        WorkInfo.State.BLOCKED,
        WorkInfo.State.ENQUEUED -> PlaylistSubscriptionPhase.ENQUEUED
        WorkInfo.State.RUNNING -> PlaylistSubscriptionPhase.RUNNING
        WorkInfo.State.SUCCEEDED -> PlaylistSubscriptionPhase.SUCCEEDED
        WorkInfo.State.FAILED -> PlaylistSubscriptionPhase.FAILED
        WorkInfo.State.CANCELLED -> PlaylistSubscriptionPhase.CANCELLED
    },
    title = tracking.title,
    source = tracking.source,
    workId = tracking.workId,
)
