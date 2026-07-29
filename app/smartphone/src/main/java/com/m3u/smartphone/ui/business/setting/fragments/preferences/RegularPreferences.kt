package com.m3u.smartphone.ui.business.setting.fragments.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.m3u.core.foundation.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.Preference
import com.m3u.smartphone.ui.material.components.SettingDestination

@Composable
internal fun RegularPreferences(
    fragment: SettingDestination,
    navigateToPlaylistManagement: () -> Unit,
    navigateToExtensionPlugins: () -> Unit,
    navigateToThemeSelector: () -> Unit,
    navigateToOptional: () -> Unit,
    codecPackEnabled: Boolean,
    navigateToCodecPack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.selectableGroup(),
    ) {
        Preference(
            title = stringResource(string.feat_setting_playlist_management),
            icon = Icons.Rounded.MusicNote,
            selected = fragment.isPlaylistDestination(),
            onClick = navigateToPlaylistManagement
        )
        Preference(
            title = stringResource(string.feat_setting_extension_plugins),
            icon = Icons.Rounded.Extension,
            selected = fragment.isExtensionPluginDestination(),
            onClick = navigateToExtensionPlugins,
            modifier = Modifier.testTag("extension-entry"),
        )
        Preference(
            title = stringResource(string.feat_setting_appearance).title(),
            icon = Icons.Rounded.ColorLens,
            selected = fragment == SettingDestination.Appearance,
            onClick = navigateToThemeSelector
        )
        Preference(
            title = stringResource(string.feat_setting_optional_features).title(),
            icon = Icons.Rounded.Tune,
            selected = fragment == SettingDestination.Optional,
            onClick = navigateToOptional
        )
        if (codecPackEnabled) {
            Preference(
                title = stringResource(string.feat_setting_codec_pack).title(),
                icon = Icons.Rounded.Download,
                selected = fragment == SettingDestination.CodecPack,
                onClick = navigateToCodecPack
            )
        }
    }
}

private fun SettingDestination.isPlaylistDestination(): Boolean = when (this) {
    SettingDestination.Playlists,
    is SettingDestination.PlaylistConfiguration,
    SettingDestination.PlaylistSourcePicker,
    is SettingDestination.PlaylistEditor,
    SettingDestination.PlaylistEpgSources,
    SettingDestination.PlaylistHiddenChannels,
    SettingDestination.PlaylistHiddenCategories -> true
    else -> false
}

private fun SettingDestination.isExtensionPluginDestination(): Boolean = when (this) {
    SettingDestination.ExtensionPlugins,
    is SettingDestination.ExtensionPluginDetails,
    is SettingDestination.ExtensionPluginAuthorization,
    is SettingDestination.ExtensionPluginSettings -> true
    else -> false
}
