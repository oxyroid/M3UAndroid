package com.m3u.feature.channel.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.m3u.ui.MonoText

@Composable
fun MaskGestureValuePanel(
    modifier: Modifier = Modifier, icon: ImageVector, value: String,
) {
    Row(
        modifier = modifier.clip(shape = RoundedCornerShape(10.dp)).background(
            color = MaterialTheme.colorScheme.onSurface
        ).padding(vertical = 5.dp, horizontal = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            imageVector = icon,
            contentDescription = "icon gesture",
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(Color.White)
        )
        Spacer(modifier = Modifier.width(4.dp))
        MonoText(
            text = value, color = Color.White, fontSize = 20.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewMaskGestureValuePanel() {
    Surface {
        MaskGestureValuePanel(
            icon = Icons.Rounded.Refresh, value = "20"
        )
    }
}