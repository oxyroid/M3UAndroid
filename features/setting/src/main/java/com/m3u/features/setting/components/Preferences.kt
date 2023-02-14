@file:Suppress("unused")

package com.m3u.features.setting.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun FoldPreference(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: @Composable () -> Unit = {}
) {
    ListItem(
        text = {
            Text(
                text = title,
                style = MaterialTheme.typography.subtitle1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textDecoration = if (enabled) null else TextDecoration.LineThrough,
                overflow = TextOverflow.Ellipsis
            )
        },
        secondaryText = subtitle?.let {
            @Composable {
                Text(
                    text = it,
                    style = MaterialTheme.typography.subtitle2,
                    maxLines = 1,
                    textDecoration = if (enabled) null else TextDecoration.LineThrough,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        singleLineSecondaryText = true,
        trailing = trailingContent,
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .background(LocalTheme.current.surface)
            .padding(
                start = LocalSpacing.current.small
            )
    )
}

@Composable
internal fun CheckBoxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    FoldPreference(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = {
            if (enabled) {
                onCheckedChange(!checked)
            }
        },
        modifier = modifier,
        trailingContent = {
            Checkbox(
                enabled = enabled,
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(
                    checkedColor = LocalTheme.current.tint,
                    uncheckedColor = LocalTheme.current.onTint
                )
            )
        }
    )
}

@Composable
internal fun TextPreference(
    title: String,
    content: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    FoldPreference(
        title = title,
        subtitle = subtitle,
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
                color = LocalTheme.current.tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}