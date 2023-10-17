package com.m3u.material.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.m3u.material.ktx.animated
import com.m3u.material.model.LocalSpacing
import com.m3u.material.model.LocalTheme

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
fun Preference(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null,
    onClick: () -> Unit = {},
    trailingContent: @Composable () -> Unit = {}
) {
    val theme = LocalTheme.current
    var focus by remember { mutableStateOf(false) }
    val actualBackgroundColor by theme.surface.animated("FoldPreferenceBackground")

    TooltipBox(
        state = rememberTooltipState(),
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (!subtitle.isNullOrEmpty()) {
                PlainTooltip {
                    Text(
                        text = subtitle.capitalize(Locale.current),
                        // FIXME: Do not specify text color.
                        color = Color(0xFFEEEEEE)
                    )
                }
            }
        }
    ) {
        ListItem(
            text = {
                val contentAlpha = if (enabled) ContentAlpha.high else ContentAlpha.medium

                CompositionLocalProvider(
                    LocalContentAlpha provides contentAlpha
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            secondaryText = subtitle?.let {
                @Composable {
                    val contentAlpha = if (enabled) ContentAlpha.medium else ContentAlpha.disabled
                    CompositionLocalProvider(
                        LocalContentAlpha provides contentAlpha
                    ) {
                        Text(
                            text = it.capitalize(Locale.current),
                            style = MaterialTheme.typography.subtitle2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                            modifier = Modifier
                                .let {
                                    if (focus) it.basicMarquee()
                                    else it
                                }
                        )
                    }
                }
            },
            singleLineSecondaryText = true,
            trailing = trailingContent,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged {
                    focus = it.hasFocus
                }
                .focusable()
                .clickable(
                    enabled = enabled,
                    onClick = onClick
                )
                .background(actualBackgroundColor)
                .padding(
                    start = LocalSpacing.current.small
                )
        )
    }
}

@Composable
fun CheckBoxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val combined = Modifier
        .toggleable(
            value = checked,
            onValueChange = { onCheckedChange(it) },
            role = Role.Checkbox
        )
        .then(modifier)
    Preference(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = {
            if (enabled) {
                onCheckedChange(!checked)
            }
        },
        modifier = combined,
        trailingContent = {
            Checkbox(
                enabled = enabled,
                checked = checked,
                onCheckedChange = null
            )
        }
    )
}

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    val combined = Modifier
        .toggleable(
            value = checked,
            onValueChange = { onCheckedChange(it) },
            role = Role.Checkbox
        )
        .then(modifier)
    Preference(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = {
            if (enabled) {
                onCheckedChange(!checked)
            }
        },
        modifier = combined,
        trailingContent = {
            Switch(
                enabled = enabled,
                checked = checked,
                onCheckedChange = null
            )
        }
    )
}

@Composable
fun IconPreference(
    title: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Preference(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        trailingContent = {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.65f),
                modifier = Modifier.minimumInteractiveComponentSize()
            )
        }
    )
}

@Composable
fun TextPreference(
    title: String,
    content: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Preference(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = {
            if (enabled) onClick()
        },
        modifier = modifier,
        trailingContent = {
            Text(
                text = content.uppercase(),
                style = MaterialTheme.typography.button,
                color = LocalTheme.current.tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(
                    horizontal = LocalSpacing.current.small
                )
            )
        }
    )
}