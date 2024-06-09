package com.m3u.feature.setting

import com.m3u.core.wrapper.Message
import com.m3u.i18n.R.string
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed class SettingMessage(
    override val level: Int,
    override val type: Int,
    override val duration: Duration = 3.seconds,
    resId: Int,
    vararg formatArgs: Any
) : Message.Static(level, "setting", type, duration, resId, formatArgs) {
    data object EmptyTitle : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_error_empty_title
    )
    data object EmptyEpgTitle : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_error_empty_epg_title
    )
    data object EmptyEpg : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_error_empty_epg
    )

    data object EmptyUrl : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_error_blank_url
    )

    data object EmptyFile : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_error_unselected_file
    )

    data object Enqueued : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_setting_enqueue_subscribe
    )

    data object EpgAdded : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_setting_epg_added
    )

    data object BackingUp : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_setting_backing_up
    )

    data object Restoring : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_setting_restoring
    )
}