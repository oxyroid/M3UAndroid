package com.m3u.features.setting.fragments

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.tv.foundation.lazy.list.TvLazyColumn
import com.m3u.features.setting.SettingFragment
import com.m3u.features.setting.fragments.preferences.ExperimentalPreference
import com.m3u.features.setting.fragments.preferences.OptionalPreferences
import com.m3u.features.setting.fragments.preferences.OtherPreferences
import com.m3u.features.setting.fragments.preferences.RegularPreferences

@Composable
internal fun PreferencesFragment(
    fragment: SettingFragment,
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    useCommonUIModeEnable: Boolean,
    navigateToPlaylistManagement: () -> Unit,
    navigateToScriptManagement: () -> Unit,
    navigateToConsole: () -> Unit,
    navigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    if (type != Configuration.UI_MODE_TYPE_TELEVISION) {
        LazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.outlineVariant)
                .then(modifier),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item {
                RegularPreferences(
                    fragment = fragment,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    navigateToPlaylistManagement = navigateToPlaylistManagement
                )
            }
            item {
                OptionalPreferences()
            }
            item {
                ExperimentalPreference(
                    navigateToScriptManagement = navigateToScriptManagement,
                    navigateToConsole = navigateToConsole
                )
            }
            item {
                OtherPreferences(
                    versionName = versionName,
                    versionCode = versionCode,
                    navigateToAbout = navigateToAbout
                )
            }
        }
    } else {
        TvLazyColumn(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.outlineVariant)
                .then(modifier),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            item {
                RegularPreferences(
                    fragment = fragment,
                    useCommonUIModeEnable = useCommonUIModeEnable,
                    navigateToPlaylistManagement = navigateToPlaylistManagement
                )
            }
            item {
                OptionalPreferences()
            }
            item {
                ExperimentalPreference(
                    navigateToScriptManagement = navigateToScriptManagement,
                    navigateToConsole = navigateToConsole
                )
            }
            item {
                OtherPreferences(
                    versionName = versionName,
                    versionCode = versionCode,
                    navigateToAbout = navigateToAbout
                )
            }
        }
    }
}
