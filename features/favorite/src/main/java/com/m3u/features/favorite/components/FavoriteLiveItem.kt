package com.m3u.features.favorite.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.m3u.data.database.entity.Live
import com.m3u.features.favorite.R
import com.m3u.ui.components.Image
import com.m3u.ui.components.TextBadge
import com.m3u.ui.model.LocalScalable
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.util.animated
import java.net.URI

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FavoriteItem(
    live: Live,
    noPictureMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scalable = LocalScalable.current
    val spacing = with(scalable) {
        LocalSpacing.current.scaled
    }
    val theme = LocalTheme.current
    val actualBackgroundColor by theme.surface.animated()
    val actualContentColor by theme.onSurface.animated()
    val scheme = remember(live) {
        URI(live.url).scheme ?: context.getString(R.string.scheme_unknown).uppercase()
    }
    Surface(
        shape = RoundedCornerShape(spacing.medium),
        color = actualBackgroundColor,
        contentColor = actualContentColor,
        elevation = spacing.none
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .then(modifier)
        ) {
            AnimatedVisibility(!noPictureMode && !live.cover.isNullOrEmpty()) {
                Image(
                    model = live.cover,
                    errorPlaceholder = live.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4 / 3f)
                )
            }
            Column(
                modifier = Modifier.padding(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Text(
                    text = live.title,
                    style = MaterialTheme.typography.subtitle1,
                    fontSize = with(scalable) {
                        MaterialTheme.typography.subtitle1.fontSize.scaled
                    },
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
                ) {
                    TextBadge(scheme)
                    CompositionLocalProvider(
                        LocalContentAlpha provides 0.6f
                    ) {
                        Text(
                            text = live.url,
                            maxLines = 1,
                            style = MaterialTheme.typography.subtitle2,
                            fontSize = with(scalable) {
                                MaterialTheme.typography.subtitle2.fontSize.scaled
                            },
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}