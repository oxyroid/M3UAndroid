package com.m3u.feature.setting.fragments.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m3u.core.unit.DataUnit
import com.m3u.ui.SettingDestination

@Composable
internal fun PreferencesFragment(
    fragment: SettingDestination,
    contentPadding: PaddingValues,
    versionName: String,
    versionCode: Int,
    navigateToPlaylistManagement: () -> Unit,
    navigateToThemeSelector: () -> Unit,
    cacheSpace: DataUnit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        item {
            RegularPreferences(
                fragment = fragment,
                navigateToPlaylistManagement = navigateToPlaylistManagement,
                navigateToThemeSelector = navigateToThemeSelector
            )
            HorizontalDivider()
        }
        item {
            OptionalPreferences()
            HorizontalDivider()
        }
        item {
            ExperimentalPreference()
            HorizontalDivider()
        }
        item {
            OtherPreferences(
                versionName = versionName,
                versionCode = versionCode,
                cacheSpace = cacheSpace,
                onClearCache = onClearCache
            )
        }
    }
}
