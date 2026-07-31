package com.m3u.smartphone.ui.business.setting.fragments.preferences

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
    entryRole: Role,
    navigateToCodecPack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.selectableGroup(),
    ) {
        SettingsSection(
            title = stringResource(string.feat_setting_section_content),
        ) {
            Preference(
                title = stringResource(string.feat_setting_playlist_management),
                content = stringResource(
                    string.feat_setting_playlist_management_description
                ),
                icon = Icons.Rounded.MusicNote,
                selected = fragment
                    .isPlaylistDestination()
                    .takeIf { entryRole == Role.Tab },
                role = entryRole,
                onClick = navigateToPlaylistManagement
            )
            Preference(
                title = stringResource(string.feat_setting_extension_plugins),
                content = stringResource(
                    string.feat_setting_extension_plugins_description
                ),
                icon = Icons.Rounded.Extension,
                selected = fragment
                    .isExtensionPluginDestination()
                    .takeIf { entryRole == Role.Tab },
                role = entryRole,
                onClick = navigateToExtensionPlugins,
                modifier = Modifier.testTag("extension-entry"),
            )
        }
        SettingsSection(
            title = stringResource(string.feat_setting_section_experience),
        ) {
            Preference(
                title = stringResource(string.feat_setting_appearance).title(),
                content = stringResource(
                    string.feat_setting_appearance_description
                ),
                icon = Icons.Rounded.ColorLens,
                selected = (fragment == SettingDestination.Appearance)
                    .takeIf { entryRole == Role.Tab },
                role = entryRole,
                onClick = navigateToThemeSelector
            )
            Preference(
                title = stringResource(
                    string.feat_setting_optional_features
                ).title(),
                content = stringResource(
                    string.feat_setting_optional_features_description
                ),
                icon = Icons.Rounded.Tune,
                selected = (fragment == SettingDestination.Optional)
                    .takeIf { entryRole == Role.Tab },
                role = entryRole,
                onClick = navigateToOptional
            )
            if (codecPackEnabled) {
                Preference(
                    title = stringResource(string.feat_setting_codec_pack).title(),
                    content = stringResource(
                        string.feat_setting_codec_pack_description
                    ),
                    icon = Icons.Rounded.Download,
                    selected = (fragment == SettingDestination.CodecPack)
                        .takeIf { entryRole == Role.Tab },
                    role = entryRole,
                    onClick = navigateToCodecPack
                )
            }
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
