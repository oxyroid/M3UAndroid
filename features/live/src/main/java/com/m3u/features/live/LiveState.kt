package com.m3u.features.live

import android.graphics.Rect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.m3u.core.annotation.ClipMode
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live

data class LiveState(
    val init: Init = InitSpecial(),
    val experimentalMode: Boolean = Configuration.DEFAULT_EXPERIMENTAL_MODE,
    @ClipMode val clipMode: Int = Configuration.DEFAULT_CLIP_MODE,
    val fullInfoPlayer: Boolean = Configuration.DEFAULT_FULL_INFO_PLAYER,
    val recording: Boolean = false,
    val message: Event<String> = handledEvent(),
    val player: Player? = null,
    val playerState: PlayerState = PlayerState(),
    val muted: Boolean = false
) {

    sealed class Init(
        open val feed: Feed? = null
    )

    data class InitSpecial(
        val live: Live? = null,
        override val feed: Feed? = null
    ) : Init()

    data class InitPlayList(
        val lives: List<Live> = emptyList(),
        override val feed: Feed? = null,
        val initialIndex: Int = 0
    ) : Init()

    data class PlayerState(
        val playback: @Player.State Int = Player.STATE_IDLE,
        val videoSize: Rect = Rect(),
        val playerError: PlaybackException? = null
    )
}
