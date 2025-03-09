package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.components.MonoText

@Composable
internal fun MaskGestureValuePanel(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Surface(
        modifier = modifier
            .clip(shape = RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(vertical = 5.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "icon-gesture",
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(spacing.extraSmall))
            MonoText(
                text = value, fontSize = 14.sp
            )
        }
    }
}
