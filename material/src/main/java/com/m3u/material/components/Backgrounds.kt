package com.m3u.material.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m3u.material.ktx.ifUnspecified

@Composable
fun Background(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    content: @Composable BoxScope.() -> Unit
) {
    val actualColor by animateColorAsState(
        targetValue = color.ifUnspecified { Color.Transparent },
        label = "background-color"
    )
    val actualContentColor by animateColorAsState(
        targetValue = contentColor.ifUnspecified { LocalContentColor.current },
        label = "content-color"
    )
    Box(
        modifier = Modifier
            .drawBehind { drawRect(actualColor) }
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
