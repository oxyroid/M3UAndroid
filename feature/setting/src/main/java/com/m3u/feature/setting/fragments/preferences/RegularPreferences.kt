package com.m3u.feature.setting.fragments.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.SettingDestination

@Composable
internal fun RegularPreferences(
    fragment: SettingDestination,
    navigateToPlaylistManagement: () -> Unit,
    navigateToThemeSelector: () -> Unit,
    navigateToOptional: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        Preference(
            title = stringResource(string.feat_setting_playlist_management).title(),
            icon = Icons.Rounded.MusicNote,
            enabled = fragment != SettingDestination.Playlists,
            onClick = navigateToPlaylistManagement
        )
        Preference(
            title = stringResource(string.feat_setting_appearance).title(),
            icon = Icons.Rounded.ColorLens,
            enabled = fragment != SettingDestination.Appearance,
            onClick = navigateToThemeSelector
        )
        Preference(
            title = stringResource(string.feat_setting_optional_features).title(),
            icon = Icons.Rounded.Extension,
            enabled = fragment != SettingDestination.Optional,
            onClick = navigateToOptional
        )
    }
}