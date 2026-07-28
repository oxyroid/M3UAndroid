package com.m3u.smartphone.ui.material.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun Preference(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: String? = null,
    elevation: Dp = 0.dp,
    selected: Boolean? = null,
    role: Role? = null,
    onClick: () -> Unit = {},
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = content?.let { supportingText ->
            @Composable {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        trailingContent = trailing,
        leadingContent = icon?.let {
            @Composable {
                Icon(imageVector = it, contentDescription = null)
            }
        },
        tonalElevation = LocalAbsoluteTonalElevation.current,
        shadowElevation = elevation,
        colors = ListItemDefaults.colors(
            containerColor = if (selected == true) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                Color.Transparent
            },
            overlineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha),
            supportingColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha),
            headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha)
        ),
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .semantics(mergeDescendants = true) {}
            .then(
                if (selected != null) {
                    Modifier.selectable(
                        selected = selected,
                        enabled = enabled,
                        role = role ?: Role.Tab,
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = ripple()
                    )
                } else {
                    Modifier.clickable(
                        enabled = enabled,
                        role = role,
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = ripple()
                    )
                }
            )
            .fillMaxWidth()
    )
}


@Composable
fun CheckBoxPreference(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    elevation: Dp = 0.dp,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Preference(
        title = title,
        content = content,
        enabled = enabled,
        elevation = elevation,
        onClick = {
            if (enabled) {
                onChanged(!checked)
            }
        },
        modifier = modifier,
        role = Role.Checkbox,
        trailing = {
            Checkbox(
                enabled = enabled,
                checked = checked,
                onCheckedChange = null
            )
        },
        icon = icon
    )
}

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    elevation: Dp = 0.dp,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Preference(
        title = title,
        content = content,
        elevation = elevation,
        enabled = enabled,
        onClick = {
            if (enabled) {
                onChanged(!checked)
            }
        },
        modifier = modifier,
        role = Role.Switch,
        trailing = {
            Switch(
                enabled = enabled,
                checked = checked,
                onCheckedChange = null
            )
        },
        icon = icon
    )
}

@Composable
fun TrailingIconPreference(
    title: String,
    trailingIcon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    elevation: Dp = 0.dp,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Preference(
        title = title,
        content = content,
        enabled = enabled,
        onClick = onClick,
        elevation = elevation,
        modifier = modifier,
        trailing = {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.65f)
            )
        },
        icon = icon
    )
}

@Composable
fun TextPreference(
    title: String,
    trailing: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    elevation: Dp = 0.dp,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Preference(
        title = title,
        content = content,
        enabled = enabled,
        elevation = elevation,
        onClick = {
            if (enabled) onClick()
        },
        modifier = modifier,
        trailing = {
            Text(
                text = trailing,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = 144.dp)
            )
        },
        icon = icon
    )
}
