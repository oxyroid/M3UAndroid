package com.m3u.material.components

import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m3u.material.ktx.animated
import com.m3u.material.ktx.ifUnspecified

@Composable
fun Background(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    content: @Composable () -> Unit
) {
    val actualBackgroundColor by color
        .ifUnspecified { Color.Transparent }
        .animated("BackgroundBackground")
    val actualContentColor by contentColor.animated("BackgroundContent")
    Surface(
        color = actualBackgroundColor,
        contentColor = actualContentColor,
        modifier = modifier
    ) {
        CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
            content()
        }
    }
}
