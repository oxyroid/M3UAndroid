package com.m3u.features.feed.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.entity.Live
import com.m3u.features.feed.R
import com.m3u.ui.components.Image
import com.m3u.ui.components.OuterColumn
import com.m3u.ui.components.TextBadge
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import java.net.URI

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveItem(
    live: Live,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val theme = LocalTheme.current
    val scheme = remember(live) {
        URI(live.url).scheme ?: context.getString(R.string.scheme_unknown).uppercase()
    }
    Card(
        shape = RectangleShape,
        backgroundColor = theme.surface,
        contentColor = theme.onSurface
    ) {
        OuterColumn(
            modifier = modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Image(
                model = live.cover,
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(spacing.small),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4 / 3f)
            )
            Text(
                text = live.title,
                style = MaterialTheme.typography.body1,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                color = if (live.favourite) theme.primary
                else Color.Unspecified,
                fontWeight = FontWeight.Bold
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                TextBadge(text = scheme)
                CompositionLocalProvider(
                    LocalContentAlpha provides 0.6f
                ) {
                    Text(
                        text = live.url,
                        maxLines = 1,
                        style = MaterialTheme.typography.body2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
