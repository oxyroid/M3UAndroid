package com.m3u.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LocalAbsoluteElevation
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.unit.dp
import com.m3u.ui.model.LocalTheme

@Composable
fun Background(
    modifier: Modifier = Modifier,
    color: Color = LocalTheme.current.background,
    contentColor: Color = LocalTheme.current.onBackground,
    content: @Composable () -> Unit
) {
    Surface(
        color = if (color.isUnspecified) Color.Transparent else color,
        contentColor = contentColor,
        modifier = modifier.fillMaxSize()
    ) {
        CompositionLocalProvider(LocalAbsoluteElevation provides 0.dp) {
            content()
        }
    }
}