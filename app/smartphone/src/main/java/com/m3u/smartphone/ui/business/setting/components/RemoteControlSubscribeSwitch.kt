package com.m3u.smartphone.ui.business.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun RemoteControlSubscribeSwitch(
    checked: Boolean,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(MaterialTheme.shapes.large)
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = { onChanged() },
                role = Role.Switch,
            )
            .padding(
                horizontal = spacing.medium,
                vertical = spacing.small,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text(
            text = stringResource(string.feat_setting_subscribe_for_tv),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            color = LocalContentColor.current.copy(alpha = if (enabled) 1f else 0.38f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
