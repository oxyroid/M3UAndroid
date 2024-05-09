package com.m3u.features.setting.fragments.preferences

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.title
import com.m3u.features.setting.components.CheckBoxSharedPreference
import com.m3u.i18n.R.string
import com.m3u.material.components.Preference
import com.m3u.material.ktx.isTelevision

@Composable
internal fun ExperimentalPreference(
    modifier: Modifier = Modifier
) {
    val preferences = hiltPreferences()
    val tv = isTelevision()

    var experimentalMode by remember { mutableStateOf(false) }
    Column(modifier) {
        Preference(
            title = stringResource(string.feat_setting_experimental_mode).title(),
            content = stringResource(string.feat_setting_experimental_mode_description),
            icon = Icons.Rounded.Dangerous,
            onClick = { experimentalMode = !experimentalMode }
        )
        AnimatedVisibility(
            visible = experimentalMode,
            enter = expandVertically(
                expandFrom = Alignment.Bottom
            ),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Bottom
            )
        ) {
            Column {
                if (!tv) {
                    CheckBoxSharedPreference(
                        title = string.feat_setting_always_television,
                        content = string.feat_setting_always_television_description,
                        icon = Icons.Rounded.Tv,
                        checked = preferences.alwaysTv,
                        onChanged = { preferences.alwaysTv = !preferences.alwaysTv }
                    )
                }
            }
        }
    }
}