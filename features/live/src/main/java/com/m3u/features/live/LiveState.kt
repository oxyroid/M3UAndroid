package com.m3u.features.live

import com.m3u.data.entity.Live

sealed class LiveState(
    open val live: Live?
) {
    data class Loading(
        override val live: Live? = null
    ) : LiveState(live)

    data class Result(
        override val live: Live? = null,
        val message: String? = null
    ) : LiveState(live)
}
