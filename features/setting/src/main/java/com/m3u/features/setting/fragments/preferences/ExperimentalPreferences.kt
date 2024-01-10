package com.m3u.features.setting.fragments.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SafetyCheck
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.title
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R
import com.m3u.material.components.Preference

@Composable
internal fun ExperimentalPreference(
    navigateToScriptManagement: () -> Unit,
    navigateToConsole: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pref = LocalPref.current
    Column(modifier) {
        val toggleableState by remember {
            derivedStateOf {
                when {
                    !pref.experimentalMode -> ToggleableState.Off
                    with(pref) { !darkMode || !isSSLVerification } -> ToggleableState.Indeterminate
                    else -> ToggleableState.On
                }
            }
        }
        Preference(
            title = stringResource(R.string.feat_setting_experimental_mode).title(),
            content = stringResource(R.string.feat_setting_experimental_mode_description),
            icon = Icons.Rounded.Dangerous,
            onClick = { pref.experimentalMode = !pref.experimentalMode },
            trailing = {
                TriStateCheckbox(
                    state = toggleableState,
                    onClick = null
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
                    title = stringResource(R.string.feat_setting_script_management).title(),
                    content = stringResource(R.string.feat_setting_not_implementation).title(),
                    icon = Icons.Rounded.Extension,
                    enabled = false,
                    onClick = navigateToScriptManagement
                )
                Preference(
                    title = stringResource(R.string.feat_setting_console_editor).title(),
                    icon = Icons.Rounded.Terminal,
                    onClick = navigateToConsole
                )
                CheckBoxSharedPreference(
                    title = R.string.feat_setting_auto_refresh,
                    content = R.string.feat_setting_auto_refresh_description,
                    icon = Icons.Rounded.Refresh,
                    checked = pref.autoRefresh,
                    onChanged = { pref.autoRefresh = !pref.autoRefresh }
                )
                CheckBoxSharedPreference(
                    title = R.string.feat_setting_ssl_verification_enabled,
                    content = R.string.feat_setting_ssl_verification_enabled_description,
                    icon = Icons.Rounded.SafetyCheck,
                    checked = pref.isSSLVerification,
                    onChanged = {
                        pref.isSSLVerification = !pref.isSSLVerification
                    }
                )
            }
        }
    }
}