package com.m3u.smartphone.ui.material.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.components.CircularProgressIndicator as FoundationCircularProgressIndicator

@Composable
fun CircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: Dp = 16.dp
) {
    FoundationCircularProgressIndicator(
        modifier = modifier,
        color = color,
        size = size
    )
}
