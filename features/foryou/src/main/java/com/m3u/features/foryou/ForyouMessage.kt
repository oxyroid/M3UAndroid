package com.m3u.features.foryou

import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string

sealed class ForyouMessage(
    resId: Int,
    vararg formatArgs: Any
) : Message(resId, *formatArgs) {
    data object ErrorCannotUnsubscribe : ForyouMessage(string.feat_foryou_error_unsubscribe_playlist)
}
