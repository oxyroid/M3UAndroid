package com.m3u.material.components.mask

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.m3u.material.ktx.isTelevision

@Composable
fun MaskCircleButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val tv = isTelevision()
    if (!tv) {
        Surface(
            shape = CircleShape,
            onClick = {
                state.wake()
                onClick()
            },
            modifier = modifier,
            color = Color.Unspecified,
            contentColor = tint.takeOrElse { LocalContentColor.current }
        ) {
            CompositionLocalProvider(
                androidx.tv.material3.LocalContentColor provides LocalContentColor.current
            ) {

            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        }
    } else {
        androidx.tv.material3.Surface(
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            onClick = {
                state.wake()
                onClick()
            },
            modifier = modifier,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Unspecified,
                contentColor = tint.takeOrElse { androidx.tv.material3.LocalContentColor.current }
            )
        ) {
            androidx.tv.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        }
    }
}