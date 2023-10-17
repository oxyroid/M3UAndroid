@file:Suppress("unused")

package com.m3u.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ProvideTextStyle
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.material.model.LocalTheme

@Composable
fun Button(
    textRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = LocalTheme.current.primary,
    contentColor: Color = LocalTheme.current.onPrimary,
    disabledBackgroundColor: Color = backgroundColor.copy(alpha = 0.12f),
    disabledContentColor: Color = backgroundColor.copy(alpha = 0.38f),
    onClick: () -> Unit
) {
    Button(
        text = stringResource(textRes),
        modifier = modifier,
        enabled = enabled,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        disabledBackgroundColor = disabledBackgroundColor,
        disabledContentColor = disabledContentColor,
        onClick = onClick
    )
}

@Composable
fun Button(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = LocalTheme.current.primary,
    contentColor: Color = LocalTheme.current.onPrimary,
    disabledBackgroundColor: Color = backgroundColor.copy(alpha = 0.12f),
    disabledContentColor: Color = backgroundColor.copy(alpha = 0.38f),
    onClick: () -> Unit
) {
    Button(
        shape = RoundedCornerShape(8.dp),
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            disabledBackgroundColor = disabledBackgroundColor,
            disabledContentColor = disabledContentColor
        )
    ) {
        Text(
            text = text.uppercase()
        )
    }
}

@Composable
fun TextButton(
    textRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = LocalTheme.current.surface,
    contentColor: Color = LocalTheme.current.primary,
    disabledContentColor: Color = LocalTheme.current.onSurface.copy(alpha = 0.38f),
    onClick: () -> Unit
) {
    TextButton(
        text = stringResource(textRes),
        modifier = modifier,
        enabled = enabled,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        disabledContentColor = disabledContentColor,
        onClick = onClick
    )
}

@Composable
fun TextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = LocalTheme.current.surface,
    contentColor: Color = LocalTheme.current.primary,
    disabledContentColor: Color = LocalTheme.current.onSurface.copy(alpha = 0.38f),
    onClick: () -> Unit
) {
    TextButtonLayout(
        modifier = modifier,
        enabled = enabled,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        disabledContentColor = disabledContentColor,
        onClick = onClick
    ) {
        Text(
            style = MaterialTheme.typography.button,
            text = text.uppercase(),
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
fun TextButtonLayout(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = LocalTheme.current.primary,
    disabledContentColor: Color = LocalTheme.current.onSurface.copy(alpha = 0.38f),
    onClick: () -> Unit = {},
    text: @Composable RowScope.() -> Unit,
) {
    TextButton(
        shape = RoundedCornerShape(8.dp),
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = backgroundColor,
            contentColor = contentColor,
            disabledContentColor = disabledContentColor
        ),
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                content = text
            )
        }
    )
}


@Composable
fun IconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint
        )
    }
}


@Composable
fun BrushButton(
    text: String,
    brush: Brush,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            ProvideTextStyle(value = MaterialTheme.typography.subtitle1) {
                Row(
                    Modifier
                        .defaultMinSize(
                            minWidth = ButtonDefaults.MinWidth,
                            minHeight = ButtonDefaults.MinHeight
                        )
                        .padding(ButtonDefaults.ContentPadding),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = {
                        Text(
                            text = text,
                            maxLines = 1
                        )
                    }
                )
            }
        }
    }
}