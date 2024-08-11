package com.m3u.feature.favorite.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R.string
import com.m3u.material.components.VerticalDraggableScrollbar
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.createPremiumBrush
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.Glow as TvGlow
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
internal fun FavouriteGallery(
    contentPadding: PaddingValues,
    channels: Resource<List<Channel>>,
    zapping: Channel?,
    recently: Boolean,
    rowCount: Int,
    onClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    onClickRandomTips: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Box(modifier) {
        when (channels) {
            Resource.Loading -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding)
                )
            }

            is Resource.Success -> {
                Row(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(start = spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    @Suppress("NAME_SHADOWING")
                    val channels = channels.data
                    val lazyStaggeredGridState = rememberLazyStaggeredGridState()

                    LazyVerticalStaggeredGrid(
                        state = lazyStaggeredGridState,
                        columns = StaggeredGridCells.Fixed(rowCount),
                        verticalItemSpacing = spacing.medium,
                        horizontalArrangement = Arrangement.spacedBy(spacing.large),
                        contentPadding = PaddingValues(vertical = spacing.medium) + contentPadding,
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f),
                    ) {
//                    item(span = StaggeredGridItemSpan.FullLine) {
//                        RandomTips(
//                            onClick = onClickRandomTips
//                        )
//                    }
                        items(
                            items = channels,
                            key = { it.id },
                            contentType = { it.cover.isNullOrEmpty() }
                        ) { channel ->
                            FavoriteItem(
                                channel = channel,
                                zapping = zapping == channel,
                                onClick = { onClick(channel) },
                                onLongClick = { onLongClick(channel) },
                                recently = recently,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    VerticalDraggableScrollbar(lazyStaggeredGridState = lazyStaggeredGridState)
                }
            }

            is Resource.Failure -> {}
        }
    }
}

@Composable
private fun RandomTips(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val tv = tv()
    val title = stringResource(string.feat_favorite_play_randomly)
    if (!tv) {
        ListItem(
            headlineContent = {
                Text(
                    text = title.title(),
                    style = MaterialTheme.typography.titleSmall,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            },
            colors = ListItemDefaults.colors(Color.Transparent),
            modifier = Modifier
                .clip(AbsoluteRoundedCornerShape(spacing.medium))
                .clickable(onClick = onClick)
                .background(
                    Brush.createPremiumBrush(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
                .then(modifier)
        )
    } else {
        TvCard(
            onClick = onClick,
            glow = TvCardDefaults.glow(
                TvGlow(
                    elevationColor = Color.Transparent,
                    elevation = spacing.small
                )
            ),
            scale = TvCardDefaults.scale(
                scale = 0.95f,
                focusedScale = 1f
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.createPremiumBrush(
                            TvMaterialTheme.colorScheme.primary,
                            TvMaterialTheme.colorScheme.tertiary
                        )
                    )
                    .padding(spacing.medium)
                    .then(modifier)
            ) {
                TvText(
                    text = title.title(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TvMaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
