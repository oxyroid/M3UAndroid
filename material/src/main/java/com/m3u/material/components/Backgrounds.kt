package com.m3u.material.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.LocalAbsoluteElevation
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m3u.material.ktx.animated
import com.m3u.material.ktx.ifUnspecified
import com.m3u.material.model.LocalTheme

@Composable
fun Background(
    modifier: Modifier = Modifier,
    color: Color = LocalTheme.current.background,
    contentColor: Color = LocalTheme.current.onBackground,
    content: @Composable () -> Unit
) {
    val actualBackgroundColor by color
        .ifUnspecified { Color.Transparent }
        .animated("BackgroundBackground")
    val actualContentColor by contentColor.animated("BackgroundContent")
    Surface(
        color = actualBackgroundColor,
        contentColor = actualContentColor,
        modifier = modifier.fillMaxSize()
    ) {
        CompositionLocalProvider(LocalAbsoluteElevation provides 0.dp) {
            content()
        }
    }
}