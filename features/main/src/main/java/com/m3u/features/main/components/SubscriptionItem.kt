package com.m3u.features.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.m3u.ui.components.basic.M3URow
import com.m3u.ui.local.LocalSpacing
import com.m3u.ui.local.LocalTheme

@Composable
internal fun SubscriptionItem(
    label: String,
    number: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(LocalSpacing.current.medium)
    ) {
        M3URow(
            modifier = modifier.clickable(
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple()
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.subtitle2,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .background(color = LocalTheme.current.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    color = LocalTheme.current.onPrimary,
                    text = number.toString(),
                    style = MaterialTheme.typography.subtitle2,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        start = LocalSpacing.current.small,
                        end = LocalSpacing.current.small,
                        bottom = 2.dp,
                    ),
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}