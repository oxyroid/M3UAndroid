package com.m3u.features.main.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.ui.components.OuterRow
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.util.animated

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FeedItem(
    label: AnnotatedString,
    number: Int,
    special: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    val actualBackgroundColor by theme.surface.animated()
    val actualContentColor by theme.onSurface.animated()
    val actualBorder by animateDpAsState(
        if (special) spacing.extraSmall
        else spacing.none
    )
    Card(
        shape = RoundedCornerShape(spacing.medium),
        backgroundColor = actualBackgroundColor,
        contentColor = actualContentColor,
        elevation = spacing.none,
        border = BorderStroke(actualBorder, Color.Black.copy(alpha = 0.12f))
    ) {
        OuterRow(
            modifier = modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            val actualPrimaryColor by theme.primary.animated()
            val actualOnPrimaryColor by theme.onPrimary.animated()
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(actualPrimaryColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    color = actualOnPrimaryColor,
                    text = number.toString(),
                    style = MaterialTheme.typography.subtitle2,
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