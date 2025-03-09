package com.m3u.material.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp

@Composable
inline fun Background(
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    shape: Shape = RectangleShape,
    crossinline content: @Composable () -> Unit
) {
    val actualColor = color.takeOrElse { MaterialTheme.colorScheme.background }
    val actualContentColor = contentColor.takeOrElse { MaterialTheme.colorScheme.onBackground }
    Box(
        modifier = Modifier
            .clip(shape)
            .drawBehind {
                drawRect(actualColor)
            }
            .then(modifier)
    ) {
        CompositionLocalProvider(
            LocalAbsoluteTonalElevation provides 0.dp,
            LocalContentColor provides actualContentColor
        ) {
            content()
        }
    }
}
