package com.m3u.material.components.mask

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import com.m3u.material.ktx.ifUnspecified
import com.m3u.material.ktx.isTvDevice

@Composable
fun MaskCircleButton(
    state: MaskState,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val tv = isTvDevice()
    if (!tv) {
        Surface(
            shape = CircleShape,
            onClick = {
                state.wake()
                onClick()
            },
            modifier = modifier,
            color = Color.Unspecified
        ) {
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
                containerColor = Color.Unspecified
            )
        ) {
            androidx.tv.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = tint.ifUnspecified { androidx.tv.material3.LocalContentColor.current }
            )
        }
    }
}