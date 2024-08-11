package com.m3u.material.components.mask

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.m3u.material.components.Icon
import com.m3u.material.ktx.tv
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.Surface as TvSurface

@Composable
fun MaskCircleButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val tv = tv()
    if (!tv) {
        Surface(
            shape = CircleShape,
            onClick = {
                state.wake()
                onClick()
            },
            modifier = modifier,
            color = Color.Unspecified,
            contentColor = tint.takeOrElse { LocalContentColor.current.copy(0.56f) }
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        }
    } else {
        TvSurface(
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            onClick = {
                state.wake()
                onClick()
            },
            modifier = modifier,
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Unspecified,
                contentColor = tint.takeOrElse { TvLocalContentColor.current.copy(0.56f) }
            )
        ) {
            TvIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(96.dp)
            )
        }
    }
}