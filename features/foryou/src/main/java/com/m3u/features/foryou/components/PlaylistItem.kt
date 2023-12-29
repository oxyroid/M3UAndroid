package com.m3u.features.foryou.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.material.components.OuterRow
import com.m3u.material.model.LocalSpacing

@Composable
internal fun PlaylistItem(
    label: AnnotatedString,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = MaterialTheme.colorScheme
    val actualContentColor by animateColorAsState(
        targetValue = theme.onSurface,
        label = "playlist-item-content"
    )

    OutlinedCard(
        shape = RoundedCornerShape(spacing.medium),
        border = CardDefaults.outlinedCardBorder(local),
        modifier = modifier.semantics(mergeDescendants = true) { }
    ) {
        OuterRow(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (local) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.DriveFileMove,
                    contentDescription = null,
                    tint = actualContentColor
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            val actualPrimaryColor by animateColorAsState(
                targetValue = theme.primary,
                label = "playlist-item-primary"
            )
            val actualOnPrimaryColor by animateColorAsState(
                targetValue = theme.onPrimary,
                label = "playlist-item-on-primary"
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(actualPrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    color = actualOnPrimaryColor,
                    text = number.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = spacing.small,
                        end = spacing.small,
                        bottom = 2.dp,
                    ),
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}