package com.m3u.material.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import com.m3u.material.ktx.ifUnspecified
import com.m3u.material.ktx.isTelevision

@Composable
inline fun Background(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.onBackground,
    crossinline content: @Composable BoxScope.() -> Unit
) {
    val actualContentColor = contentColor.ifUnspecified {
        if (!isTelevision()) LocalContentColor.current
        else androidx.tv.material3.LocalContentColor.current
    }
    val currentColor by animateColorAsState(
        targetValue = color,
        label = "color",
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    val currentContentColor by animateColorAsState(
        targetValue = actualContentColor,
        label = "content-color",
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
    )
    Box(
        modifier = Modifier
            .drawBehind {
                if (currentColor.isSpecified) drawRect(currentColor)
            }
            .then(modifier)
    ) {
        CompositionLocalProvider(
            LocalAbsoluteTonalElevation provides 0.dp,
            LocalContentColor provides currentContentColor,
            androidx.tv.material3.LocalContentColor provides currentContentColor
        ) {
            content()
        }
    }
}
