package com.m3u.material.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.surfaceColorAtElevation
import com.m3u.material.ktx.isTvDevice
import com.m3u.material.model.LocalSpacing

@Composable
fun TextBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val tv = isTvDevice()
    val tvColorScheme = androidx.tv.material3.MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(spacing.small),
        colors = CardDefaults.cardColors(
            containerColor = if (tv) tvColorScheme.surfaceColorAtElevation(4.dp)
            else Color.Unspecified,
            contentColor = if (tv) tvColorScheme.onSurface else Color.Unspecified
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.invoke()
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(
                    horizontal = spacing.extraSmall
                )
            )
        }
    }
}
