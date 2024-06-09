package com.m3u.feature.playlist.configuration.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.util.basic.title
import com.m3u.i18n.R
import com.m3u.material.components.SelectionsDefaults
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape

@Composable
internal fun AutoSyncProgrammesButton(
    checked: Boolean,
    onCheckedChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.feat_playlist_configuration_auto_refresh_programmes).title(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = stringResource(R.string.feat_playlist_configuration_auto_refresh_programmes_description),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        },
        colors = ListItemDefaults.colors(
            supportingColor = LocalContentColor.current.copy(0.38f)
        ),
        modifier = Modifier
            .border(
                1.dp,
                LocalContentColor.current.copy(0.38f),
                SelectionsDefaults.Shape
            )
            .clip(AbsoluteSmoothCornerShape(spacing.medium, 65))
            .clickable(
                onClick = onCheckedChange
            )
            .then(modifier)
    )
}