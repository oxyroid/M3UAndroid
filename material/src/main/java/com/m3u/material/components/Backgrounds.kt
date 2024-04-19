package com.m3u.material.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import com.m3u.material.ktx.isTelevision

@Composable
// TODO: check drawBehind but not surface is necessary or not.
inline fun Background(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = MaterialTheme.colorScheme.contentColorFor(color),
    crossinline content: @Composable () -> Unit
) {
    val actualContentColor = contentColor.takeOrElse {
        if (!isTelevision()) LocalContentColor.current
        else androidx.tv.material3.LocalContentColor.current
    }
//    val currentColor by animateColorAsState(
//        targetValue = color,
//        label = "color",
//        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
//    )
//    val currentContentColor by animateColorAsState(
//        targetValue = actualContentColor,
//        label = "content-color",
//        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
//    )
    Box(
        modifier = Modifier
            .drawBehind {
                drawRect(color)
//                if (currentColor.isSpecified) drawRect(currentColor)
            }
            .then(modifier)
    ) {
        CompositionLocalProvider(
            LocalAbsoluteTonalElevation provides 0.dp,
            LocalContentColor provides actualContentColor,
            androidx.tv.material3.LocalContentColor provides actualContentColor
        ) {
            content()
        }
    }
}
