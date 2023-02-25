package com.m3u.features.live

import com.m3u.core.wrapper.Event
import com.m3u.core.wrapper.handledEvent
import com.m3u.data.entity.Live

data class LiveState(
    val live: Live? = null,
    val recording: Boolean = false,
    val message: Event<String> = handledEvent(),
) {
    sealed interface Init {
        data class SingleLive(
            val live: Live? = null
        ) : Init

        data class PlayList(
            val lives: List<Live> = emptyList(),
            val initialIndex: Int = 0
        ) : Init
    }
}
