package com.m3u.feature.setting.components

import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.i18n.R.string
import com.m3u.material.components.ToggleableSelection

@Composable
internal fun LocalStorageSwitch(
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ToggleableSelection(
        checked = checked,
        onChanged = onChanged,
        modifier = modifier,
        enabled = enabled
    ) {
        Text(
            text = stringResource(string.feat_setting_local_storage).uppercase(),
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
