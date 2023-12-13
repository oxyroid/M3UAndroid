package com.m3u.features.foryou

import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string

sealed class MainMessage(
    resId: Int,
    vararg formatArgs: Any
) : Message(resId, *formatArgs) {
    data object ErrorCannotUnsubscribe : MainMessage(string.feat_main_error_unsubscribe_playlist)
}
