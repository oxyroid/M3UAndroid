package com.m3u.features.setting.fragments.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.title
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference

@Composable
internal fun ExperimentalPreference(
    navigateToScriptManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pref = LocalPref.current

    Column(modifier) {
        Preference(
            title = stringResource(string.feat_setting_experimental_mode).title(),
            content = stringResource(string.feat_setting_experimental_mode_description),
            icon = Icons.Rounded.Dangerous,
            onClick = { pref.experimentalMode = !pref.experimentalMode },
            trailing = {
                Checkbox(
                    checked = pref.experimentalMode,
                    onCheckedChange = null
                )
            }
        )
        AnimatedVisibility(
            visible = pref.experimentalMode,
            enter = expandVertically(
                expandFrom = Alignment.Bottom
            ),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom
            )
        ) {
            Column {
                Preference(
                    title = stringResource(string.feat_setting_script_management).title(),
                    content = stringResource(string.feat_setting_not_implementation).title(),
                    icon = Icons.Rounded.Extension,
                    enabled = false,
                    onClick = navigateToScriptManagement
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_auto_refresh,
                    content = string.feat_setting_auto_refresh_description,
                    icon = Icons.Rounded.Refresh,
                    checked = pref.autoRefresh,
                    onChanged = { pref.autoRefresh = !pref.autoRefresh }
                )
                CheckBoxSharedPreference(
                    title = string.feat_setting_always_television,
                    content = string.feat_setting_always_television_description,
                    icon = Icons.Rounded.Tv,
                    checked = pref.alwaysTv,
                    onChanged = { pref.alwaysTv = !pref.alwaysTv }
                )
            }
        }
    }
}