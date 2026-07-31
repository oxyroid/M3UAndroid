package com.m3u.smartphone.ui.business.favourite.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.PageStateContent
import com.m3u.smartphone.ui.material.ktx.plus
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
internal fun FavoriteGallery(
    contentPadding: PaddingValues,
    channels: LazyPagingItems<Channel>,
    zapping: Channel?,
    recently: Boolean,
    rowCount: Int,
    onClick: (Channel) -> Unit,
    onLongClick: (Channel) -> Unit,
    onBrowsePlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val refreshState = channels.loadState.refresh
    val noPictureMode by preferenceOf(PreferencesKeys.NO_PICTURE_MODE)

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = stringResource(string.ui_title_favourite),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(
                    start = spacing.large,
                    top = spacing.large,
                    end = spacing.large,
                    bottom = spacing.small,
                )
                .semantics { heading() },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                refreshState is LoadState.Loading && channels.itemCount == 0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(string.ui_state_loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = spacing.medium),
                        )
                    }
                }

                refreshState is LoadState.Error && channels.itemCount == 0 -> {
                    PageStateContent(
                        icon = Icons.Rounded.CloudOff,
                        title = stringResource(string.feat_favorite_load_error_title),
                        description = stringResource(
                            string.feat_favorite_load_error_description
                        ),
                        actionLabel = stringResource(string.ui_action_retry),
                        onAction = channels::retry,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    )
                }

                refreshState is LoadState.NotLoading && channels.itemCount == 0 -> {
                    PageStateContent(
                        icon = Icons.Rounded.FavoriteBorder,
                        title = stringResource(string.feat_favorite_empty_title),
                        description = stringResource(string.feat_favorite_empty_description),
                        actionLabel = stringResource(string.feat_favorite_browse_playlists),
                        actionIcon = Icons.AutoMirrored.Rounded.List,
                        onAction = onBrowsePlaylists,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    )
                }

                else -> {
                    val lazyStaggeredGridState = rememberLazyStaggeredGridState()

                    LazyVerticalStaggeredGrid(
                        state = lazyStaggeredGridState,
                        columns = StaggeredGridCells.Fixed(rowCount.coerceAtLeast(2)),
                        verticalItemSpacing = 24.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding =
                            PaddingValues(
                                horizontal = spacing.medium,
                                vertical = spacing.medium,
                            ) + contentPadding,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            count = channels.itemCount,
                            key = channels.itemKey { it.id },
                            span = { index ->
                                val channel = channels.peek(index)
                                if (
                                    channel != null &&
                                    (noPictureMode || channel.cover.isNullOrBlank())
                                ) {
                                    StaggeredGridItemSpan.FullLine
                                } else {
                                    StaggeredGridItemSpan.SingleLane
                                }
                            },
                        ) {
                            val channel = channels[it]
                            if (channel == null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16 / 9f),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            } else {
                                FavoriteItem(
                                    channel = channel,
                                    zapping = zapping == channel,
                                    onClick = { onClick(channel) },
                                    onLongClick = { onLongClick(channel) },
                                    recently = recently,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
