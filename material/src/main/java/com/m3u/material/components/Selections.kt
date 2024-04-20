package com.m3u.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.material.model.LocalSpacing

@Composable
fun ClickableSelection(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    Selection(
        enabled = enabled,
        color = color,
        contentColor = contentColor,
        modifier = modifier.then(Modifier.clickable(enabled = enabled, onClick = onClick)),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}

@Composable
fun ToggleableSelection(
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.SpaceBetween,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit
) {
    Selection(
        enabled = enabled,
        color = color,
        contentColor = contentColor,
        modifier = modifier.then(
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                onValueChange = { onChanged(!checked) },
                role = Role.Checkbox
            )
        ),
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        content = content
    )
}

@Composable
private fun Selection(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(LocalSpacing.current.medium),
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    val spacing = LocalSpacing.current
    val actualColor = color.takeOrElse { Color.Transparent }
    val actualContentColor = contentColor.takeOrElse {
        MaterialTheme.colorScheme.contentColorFor(actualColor).takeOrElse {
            LocalContentColor.current
        }
    }
    Row(
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SelectionsDefaults.MinHeight)
            .clip(SelectionsDefaults.Shape)
            .background(actualColor)
            .then(modifier)
            .padding(horizontal = spacing.medium)
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = actualContentColor.copy(
                    if (enabled) 1f else 0.38f
                )
            ),
            LocalContentColor provides actualContentColor,
            content = { content() }
        )
    }
}

object SelectionsDefaults {
    val Shape = AbsoluteRoundedCornerShape(16.dp)
    val MinHeight = 56.dp
}
