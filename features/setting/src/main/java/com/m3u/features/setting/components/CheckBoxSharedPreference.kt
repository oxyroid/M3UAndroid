package com.m3u.features.setting.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.m3u.core.util.basic.title
import com.m3u.material.components.CheckBoxPreference

@Composable
fun CheckBoxSharedPreference(
    title: Int,
    checked: Boolean,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    content: Int? = null,
) {
    CheckBoxPreference(
        title = stringResource(title).title(),
        content = content?.let { stringResource(it) }?.title(),
        enabled = enabled,
        checked = checked,
        icon = icon,
        onChanged = { newValue ->
            if (newValue != checked) {
                onChanged()
            }
        },
        modifier = modifier
    )
}