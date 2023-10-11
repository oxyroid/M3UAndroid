package com.m3u.features.main.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ContentAlpha
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
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
import com.m3u.ui.ktx.animateColor
import com.m3u.ui.ktx.animateDp
import com.m3u.ui.ktx.animated
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FeedItem(
    label: AnnotatedString,
    number: Int,
    local: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    val actualBackgroundColor by theme.surface.animated("FeedItemBackground")
    val actualContentColor by theme.onSurface.animated("FeedItemContent")
    val actualBorderDp by animateDp("FeedItemBorder") {
        if (local) spacing.extraSmall
        else spacing.none
    }
    val actualBorderColor by animateColor("FeedItemBorderColor") {
        if (local) Color.Black.copy(alpha = 0.12f)
        else Color.Transparent
    }
    Surface(
        shape = RoundedCornerShape(spacing.medium),
        color = actualBackgroundColor,
        contentColor = actualContentColor,
        elevation = spacing.none,
        border = BorderStroke(actualBorderDp, actualBorderColor)
    ) {
        OuterRow(
            modifier = modifier
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
                    tint = actualContentColor.copy(alpha = ContentAlpha.medium)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.body1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            val actualPrimaryColor by theme.primary.animated("FeedItemPrimary")
            val actualOnPrimaryColor by theme.onPrimary.animated("FeedItemOnPrimary")
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