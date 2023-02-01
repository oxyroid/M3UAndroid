@file:Suppress("unused")

package com.m3u.features.setting.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.m3u.ui.model.LocalTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun FoldItem(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit = {}
) {
    ListItem(
        text = {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1
            )
        },
        trailing = trailingContent,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
    )
}

@Composable
internal fun CheckBoxItem(
    title: String,
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    FoldItem(
        title = title,
        enabled = enabled,
        onClick = {
            if (enabled) {
                onCheckedChange(!checked)
            }
        },
        modifier = modifier,
        trailingContent = {
            Checkbox(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
internal fun TextItem(
    title: String,
    enabled: Boolean,
    content: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FoldItem(
        title = title,
        enabled = enabled,
        onClick = {
            if (enabled) {
                onClick()
            }
        },
        modifier = modifier,
        trailingContent = {
            Text(
                text = content,
                style = MaterialTheme.typography.button,
                color = LocalTheme.current.tint
            )
        }
    )
}