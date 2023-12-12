package com.m3u.features.live

import android.graphics.Rect
import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import org.fourthline.cling.model.meta.Device

data class LiveState(
    val recording: Boolean = false,
    val connected: Device<*, *, *>? = null
) {
    @Immutable
    data class PlayerState(
        val playState: @Player.State Int = Player.STATE_IDLE,
        val videoSize: Rect = Rect(),
        val playerError: PlaybackException? = null,
        val player: Player? = null
    )

    @Immutable
    data class Metadata(
        val feed: Feed? = null,
        val live: Live? = null
    )
}
