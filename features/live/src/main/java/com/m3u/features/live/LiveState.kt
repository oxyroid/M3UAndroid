package com.m3u.features.live

import android.graphics.Rect
import androidx.compose.runtime.Immutable
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import org.fourthline.cling.model.meta.Device

data class LiveState(
    val init: Init = InitOne(),
    val recording: Boolean = false,
    val connected: Device<*, *, *>? = null
) {
    sealed class Init(
        open val feed: Feed? = null
    )

    data class InitOne(
        val live: Live? = null,
        override val feed: Feed? = null
    ) : Init()

    data class InitPlayList(
        val lives: List<Live> = emptyList(),
        override val feed: Feed? = null,
        val initialIndex: Int = 0
    ) : Init()

    @Immutable
    data class PlayerState(
        val playState: @Player.State Int = Player.STATE_IDLE,
        val videoSize: Rect = Rect(),
        val playerError: PlaybackException? = null,
        val player: Player? = null,
    )
}
