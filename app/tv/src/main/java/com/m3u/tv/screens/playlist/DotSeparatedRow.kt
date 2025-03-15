package com.m3u.tv.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun DotSeparatedRow(
    modifier: Modifier = Modifier,
    texts: List<String>
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        texts.forEachIndexed { index, text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Normal
                )
            )
            if (index != texts.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 1f))
                        .size(4.dp)
                )
            }
        }
    }
}
