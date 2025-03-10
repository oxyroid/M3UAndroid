package com.m3u.tv.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.tv.material3.MaterialTheme
import com.m3u.tv.createScheme
import com.m3u.tv.utils.Indigo300

@Composable
fun JetStreamTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme =
            // TODO:
            remember { createScheme(Indigo300.toArgb(), true) }
        ,
        shapes = MaterialTheme.shapes,
        typography = Typography,
        content = content
    )
}
