@file:Suppress("unused")

package com.m3u.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.core.model.Icon
import com.m3u.ui.local.LocalTheme

@Composable
fun M3UButton(
    textRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = LocalTheme.current.tint,
    contentColor: Color = LocalTheme.current.onTint,
    disabledBackgroundColor: Color = backgroundColor.copy(alpha = 0.12f),
    disabledContentColor: Color = backgroundColor.copy(alpha = 0.38f),
    onClick: () -> Unit
) {
    M3UButton(
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
fun M3UButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = LocalTheme.current.tint,
    contentColor: Color = LocalTheme.current.onTint,
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
            text = text
        )
    }
}

@Composable
fun M3UTextButton(
    textRes: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = LocalTheme.current.tint,
    disabledContentColor: Color = LocalTheme.current.onSurface.copy(alpha = 0.38f),
    onClick: () -> Unit
) {
    M3UTextButton(
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
fun M3UTextButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    contentColor: Color = LocalTheme.current.tint,
    disabledContentColor: Color = LocalTheme.current.onSurface.copy(alpha = 0.38f),
    onClick: () -> Unit
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
        )
    ) {
        Text(
            style = MaterialTheme.typography.button,
            text = text,
            fontSize = 14.sp,
            maxLines = 1
        )
    }
}

@Composable
fun M3UIconButton(
    icon: Icon,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        when (icon) {
            is Icon.DrawableResourceIcon -> {
                Icon(
                    painter = painterResource(icon.id),
                    contentDescription = contentDescription
                )
            }

            is Icon.ImageVectorIcon -> {
                Icon(
                    imageVector = icon.imageVector,
                    contentDescription = contentDescription
                )
            }
        }
    }
}


@Composable
fun M3UBrushButton(
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