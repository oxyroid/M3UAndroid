package com.m3u.features.live

import com.m3u.core.annotation.ClipMode
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent

data class LiveState(
    val init: Init = Init.Live(),
    val experimentalMode: Boolean = Configuration.DEFAULT_EXPERIMENTAL_MODE,
    @ClipMode val clipMode: Int = Configuration.DEFAULT_CLIP_MODE,
    val recording: Boolean = false,
    val message: Event<String> = handledEvent(),
) {
    sealed interface Init {
        data class Live(
            val live: com.m3u.data.database.entity.Live? = null
        ) : Init

        data class PlayList(
            val lives: List<com.m3u.data.database.entity.Live> = emptyList(),
            val initialIndex: Int = 0
        ) : Init
    }
}
