package com.m3u.feature.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.i18n.R.string
import com.m3u.material.model.LocalSpacing

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
            .height(56.dp)
            .clip(RoundedCornerShape(25))
            .toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = { onChanged() },
                role = Role.Checkbox,
            )
            .padding(horizontal = spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        Text(
            text = stringResource(string.feat_setting_subscribe_for_tv).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
            color = LocalContentColor.current.copy(0.38f).takeUnless { enabled } ?: Color.Unspecified
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
