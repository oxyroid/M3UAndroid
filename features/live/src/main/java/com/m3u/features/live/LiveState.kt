package com.m3u.features.live

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.m3u.core.annotation.ClipMode
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.data.database.entity.Feed
import com.m3u.data.database.entity.Live
import net.mm2d.upnp.Device

data class LiveState(
    val init: Init = InitSingle(),
    private val configuration: Configuration,
    val recording: Boolean = false,
    val player: Player? = null,
    val playerState: PlayerState = PlayerState(),
    val muted: Boolean = false,
    val connectedDevices: List<Device> = emptyList()
) {
    sealed class Init(
        open val feed: Feed? = null
    )

    data class InitSingle(
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

    var experimentalMode: Boolean by configuration.experimentalMode

    @ClipMode
    var clipMode: Int by configuration.clipMode
    var fullInfoPlayer: Boolean by configuration.fullInfoPlayer
}
