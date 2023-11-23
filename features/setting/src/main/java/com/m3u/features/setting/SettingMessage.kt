package com.m3u.features.setting

import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string

sealed class SettingMessage(
    resId: Int,
    vararg formatArgs: Any
) : Message(resId, *formatArgs) {
    data object EmptyTitle : SettingMessage(string.feat_setting_error_empty_title)
    data object EmptyUrl : SettingMessage(string.feat_setting_error_blank_url)
    data object EmptyFile : SettingMessage(string.feat_setting_error_unselected_file)
    data object Enqueued : SettingMessage(string.feat_setting_enqueue_subscribe)
}