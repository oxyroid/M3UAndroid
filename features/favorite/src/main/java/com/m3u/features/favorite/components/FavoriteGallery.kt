package com.m3u.features.favorite.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.foundation.lazy.grid.TvGridCells
import androidx.tv.foundation.lazy.grid.TvGridItemSpan
import androidx.tv.foundation.lazy.grid.TvLazyVerticalGrid
import androidx.tv.foundation.lazy.grid.items
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import com.m3u.core.util.basic.title
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Stream
import com.m3u.i18n.R.string
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.plus
import com.m3u.material.model.LocalSpacing
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
internal fun FavouriteGallery(
    contentPadding: PaddingValues,
    streamsResource: Resource<List<Stream>>,
    zapping: Stream?,
    recently: Boolean,
    rowCount: Int,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    onClickRandomTips: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        when (streamsResource) {
            Resource.Loading -> {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(contentPadding)
                )
            }

            is Resource.Success -> {
                FavouriteGalleryImpl(
                    contentPadding = contentPadding,
                    streams = streamsResource.data,
                    zapping = zapping,
                    recently = recently,
                    rowCount = rowCount,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    onClickRandomTips = onClickRandomTips
                )
            }

            is Resource.Failure -> {}
        }
    }
}

@Composable
private fun FavouriteGalleryImpl(
    contentPadding: PaddingValues,
    streams: List<Stream>,
    zapping: Stream?,
    recently: Boolean,
    rowCount: Int,
    onClick: (Stream) -> Unit,
    onLongClick: (Stream) -> Unit,
    onClickRandomTips: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val tv = isTelevision()
    if (!tv) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(rowCount),
            verticalItemSpacing = spacing.medium,
            horizontalArrangement = Arrangement.spacedBy(spacing.large),
            contentPadding = PaddingValues(spacing.medium) + contentPadding,
            modifier = modifier.fillMaxSize(),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                RandomTips(
                    onClick = onClickRandomTips
                )
            }
            items(
                items = streams,
                key = { it.id },
                contentType = { it.cover.isNullOrEmpty() }
            ) { stream ->
                FavoriteItem(
                    stream = stream,
                    zapping = zapping == stream,
                    onClick = { onClick(stream) },
                    onLongClick = { onLongClick(stream) },
                    recently = recently,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    } else {
        TvLazyVerticalGrid(
            columns = TvGridCells.Fixed(rowCount),
            verticalArrangement = Arrangement.spacedBy(spacing.large),
            horizontalArrangement = Arrangement.spacedBy(spacing.large),
            contentPadding = PaddingValues(
                vertical = spacing.medium,
                horizontal = spacing.large
            ) + contentPadding,
            modifier = modifier.fillMaxSize(),
        ) {
            item(span = { TvGridItemSpan(rowCount) }) {
                RandomTips(
                    onClick = onClickRandomTips
                )
            }
            items(
                items = streams,
                key = { it.id },
                contentType = { it.cover.isNullOrEmpty() }
            ) { stream ->
                FavoriteItem(
                    stream = stream,
                    zapping = zapping == stream,
                    recently = recently,
                    onClick = { onClick(stream) },
                    onLongClick = { onLongClick(stream) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RandomTips(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val tv = isTelevision()
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
                    createPremiumBrush(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary
                    )
                )
                .then(modifier)
        )
    } else {
        Card(
            onClick = onClick,
            glow = CardDefaults.glow(
                Glow(
                    elevationColor = Color.Transparent,
                    elevation = spacing.small
                )
            ),
            scale = CardDefaults.scale(
                scale = 0.95f,
                focusedScale = 1f
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        createPremiumBrush(
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

@Composable
private fun createPremiumBrush(
    color1: Color = MaterialTheme.colorScheme.primaryContainer,
    color2: Color = MaterialTheme.colorScheme.secondaryContainer
): Brush {
    val transition = rememberInfiniteTransition("premium-brush")

    val leftColor by transition.animateColor(
        initialValue = color1,
        targetValue = color2,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "left"
    )
    val rightColor by transition.animateColor(
        initialValue = color2,
        targetValue = color1,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "right"
    )
    return Brush.linearGradient(
        colors = listOf(leftColor, rightColor)
    )
}