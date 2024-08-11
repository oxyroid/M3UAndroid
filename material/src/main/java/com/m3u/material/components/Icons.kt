package com.m3u.material.components

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.material.ktx.tv

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified
) {
    val tv = tv()
    if (!tv) {
        androidx.compose.material3.Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint.takeOrElse { LocalContentColor.current }
        )
    } else {
        androidx.tv.material3.Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tint.takeOrElse { androidx.tv.material3.LocalContentColor.current }
        )
    }
}