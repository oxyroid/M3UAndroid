@file:Suppress("unused")

package com.m3u.features.setting.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ListItem
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.m3u.core.util.basic.title
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.util.animated

@OptIn(
    ExperimentalMaterialApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class
)
@Composable
internal fun Preference(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    subtitle: String? = null,
    trailingContent: @Composable () -> Unit = {}
) {
    val theme = LocalTheme.current
    var hasFocus by remember { mutableStateOf(false) }
    val actualBackgroundColor by theme.surface.animated("FoldPreferenceBackground")

    TooltipBox(
        state = rememberTooltipState(),
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (!subtitle.isNullOrEmpty()) {
                PlainTooltip {
                    Text(
                        text = subtitle,
                        // FIXME: Do not specify text color.
                        color = Color(0xFFEEEEEE)
                    )
                }
            }
        }
    ) {
        ListItem(
            text = {
                Text(
                    text = title.title(),
                    style = MaterialTheme.typography.subtitle1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            secondaryText = subtitle?.let {
                @Composable {
                    Text(
                        text = it.capitalize(Locale.current),
                        style = MaterialTheme.typography.subtitle2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .let {
                                if (hasFocus) it.basicMarquee()
                                else it
                            }
                    )
                }
            },
            singleLineSecondaryText = true,
            trailing = trailingContent,
            modifier = modifier
                .fillMaxWidth()
                .onFocusChanged {
                    hasFocus = it.hasFocus
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
internal fun CheckBoxPreference(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Preference(
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
                    checkmarkColor = LocalTheme.current.onPrimary,
                    checkedColor = LocalTheme.current.primary,
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