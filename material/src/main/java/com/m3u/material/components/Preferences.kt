package com.m3u.material.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.m3u.material.ktx.isTvDevice
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.ListItemDefaults as TvListItemDefaults

@Composable
fun Preference(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: String? = null,
    elevation: Dp = Dp.Unspecified,
    onClick: () -> Unit = {},
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    // val configuration = LocalConfiguration.current
    // val type = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK

    val interactionSource = remember { MutableInteractionSource() }
    val focus by interactionSource.collectIsFocusedAsState()

    TooltipBox(
        state = rememberTooltipState(),
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (!content.isNullOrEmpty()) {
                PlainTooltip {
                    Text(
                        text = content.capitalize(Locale.current)
                    )
                }
            }
        }
    ) {
        val alpha = if (enabled) 1f else 0.38f
        val currentContainerColor by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.surface.copy(alpha),
            label = "preference-container-color",
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )
        val currentContentColor by animateColorAsState(
            targetValue = MaterialTheme.colorScheme.onSurface.copy(alpha),
            label = "preference-content-color",
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )
        if (!isTvDevice()) {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    if (content != null) {
                        Text(
                            text = content.capitalize(Locale.current),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier then if (focus) Modifier.basicMarquee()
                            else Modifier
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
                colors = ListItemDefaults.colors(
                    containerColor = currentContainerColor,
                    headlineColor = currentContentColor,
                    leadingIconColor = currentContentColor,
                    overlineColor = currentContentColor,
                    supportingColor = currentContentColor,
                    trailingIconColor = currentContentColor,
                ),
                shadowElevation = elevation,
                modifier = modifier
                    .semantics(mergeDescendants = true) {}
                    .clickable(
                        enabled = enabled,
                        onClick = onClick,
                        interactionSource = interactionSource,
                        indication = rememberRipple()
                    )
                    .fillMaxWidth()
            )
        } else {
            TvListItem(
                selected = focus,
                interactionSource = interactionSource,
                headlineContent = {
                    androidx.tv.material3.Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    if (content != null) {
                        androidx.tv.material3.Text(
                            text = content.capitalize(Locale.current),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier then if (focus) Modifier.basicMarquee()
                            else Modifier
                        )
                    }
                },
                trailingContent = trailing,
                leadingContent = {
                    icon?.let {
                        androidx.tv.material3.Icon(imageVector = it, contentDescription = null)
                    }
                },
                tonalElevation = LocalAbsoluteTonalElevation.current,
                colors = TvListItemDefaults.colors(
                    containerColor = currentContainerColor,
                    contentColor = currentContentColor,
                ),
                scale = TvListItemDefaults.scale(
                    scale = 0.9f,
                    focusedScale = 1f
                ),
                onClick = onClick,
                modifier = modifier
                    .semantics(mergeDescendants = true) {}
                    .fillMaxWidth()
            )
        }
    }
}


@Composable
fun CheckBoxPreference(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    elevation: Dp = Dp.Unspecified,
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
    elevation: Dp = Dp.Unspecified,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val combined = Modifier
        .toggleable(
            value = checked,
            onValueChange = { onChanged(it) },
            role = Role.Checkbox
        )
        .then(modifier)
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
        modifier = combined,
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
fun IconPreference(
    title: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    elevation: Dp = Dp.Unspecified,
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
                imageVector = imageVector,
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
    elevation: Dp = Dp.Unspecified,
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
                text = trailing.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        icon = icon
    )
}
