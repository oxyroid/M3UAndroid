package com.m3u.smartphone.ui.business.setting.fragments

import androidx.annotation.StringRes
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.i18n.R.string

@StringRes
internal fun extensionCapabilityNameResource(capabilityId: String): Int? = when (capabilityId) {
    ExtensionCapabilityIds.Network.id ->
        string.feat_setting_extension_capability_name_network
    ExtensionCapabilityIds.CredentialRead.id ->
        string.feat_setting_extension_capability_name_credential_read
    ExtensionCapabilityIds.CredentialWrite.id ->
        string.feat_setting_extension_capability_name_credential_write
    ExtensionCapabilityIds.SubscriptionRead.id ->
        string.feat_setting_extension_capability_name_subscription_read
    ExtensionCapabilityIds.SubscriptionWrite.id ->
        string.feat_setting_extension_capability_name_subscription_write
    ExtensionCapabilityIds.PlaybackResolve.id ->
        string.feat_setting_extension_capability_name_playback_resolve
    ExtensionCapabilityIds.EpgRead.id ->
        string.feat_setting_extension_capability_name_epg_read
    ExtensionCapabilityIds.MetadataWrite.id ->
        string.feat_setting_extension_capability_name_metadata_write
    ExtensionCapabilityIds.SettingsContribute.id ->
        string.feat_setting_extension_capability_name_settings_contribute
    ExtensionCapabilityIds.SearchRead.id ->
        string.feat_setting_extension_capability_name_search_read
    ExtensionCapabilityIds.BackgroundTask.id ->
        string.feat_setting_extension_capability_name_background_task
    else -> null
}
