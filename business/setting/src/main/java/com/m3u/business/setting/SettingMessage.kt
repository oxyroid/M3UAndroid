package com.m3u.business.setting

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

    data object WebDropNoSubscribe : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_error_webdrop_no_subscribe
    )

    data object USBEncryptionEnabled : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        duration = 5.seconds,
        resId = string.feat_setting_usb_encryption_enabled_success
    )

    data object USBEncryptionDisabled : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_setting_usb_encryption_disabled_success
    )

    data class USBEncryptionError(val message: String) : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        duration = 5.seconds,
        resId = string.feat_setting_usb_encryption_error,
        formatArgs = arrayOf(message)
    )

    data object USBNotConnected : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_usb_encryption_not_connected
    )

    // PIN Encryption Messages
    data object PINInvalid : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_pin_invalid
    )

    data object PINEncryptionEnabled : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        duration = 5.seconds,
        resId = string.feat_setting_pin_encryption_enabled_success
    )

    data object PINEncryptionDisabled : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_setting_pin_encryption_disabled_success
    )

    data class PINEncryptionError(val message: String) : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        duration = 5.seconds,
        resId = string.feat_setting_pin_encryption_error,
        formatArgs = arrayOf(message)
    )

    data object PINIncorrect : SettingMessage(
        level = LEVEL_ERROR,
        type = TYPE_SNACK,
        resId = string.feat_setting_pin_incorrect
    )

    data object PINUnlocked : SettingMessage(
        level = LEVEL_INFO,
        type = TYPE_SNACK,
        resId = string.feat_setting_pin_unlocked
    )
}