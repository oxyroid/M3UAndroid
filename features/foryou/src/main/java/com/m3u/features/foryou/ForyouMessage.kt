package com.m3u.features.foryou

import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class ForyouMessage(
    override val level: Int,
    override val type: Int,
    override val duration: Duration = 3.seconds,
    resId: Int,
    vararg formatArgs: Any
) : Message.Static(level, "foryou", type, duration, resId, formatArgs) {
    data object ErrorCannotUnsubscribe : ForyouMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_foryou_error_unsubscribe_playlist
    )
}
