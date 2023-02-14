package com.m3u.features.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.ui.components.OuterRow
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
internal fun FeedItem(
    label: String,
    number: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.subtitle1,
) {
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    Card(
        shape = RoundedCornerShape(spacing.medium),
        backgroundColor = theme.surface,
        contentColor = theme.onSurface,
    ) {
        OuterRow(
            modifier = modifier
                .clickable(
                    onClick = onClick
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(theme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    color = theme.onPrimary,
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