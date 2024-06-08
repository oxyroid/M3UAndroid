package com.m3u.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.parser.xtream.XtreamChannelInfo
import com.m3u.material.components.BottomSheet
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape

@Composable
fun EpisodesBottomSheet(
    series: Channel?,
    episodes: Resource<List<XtreamChannelInfo.Episode>>,
    onRefresh: () -> Unit,
    onEpisodeClick: (XtreamChannelInfo.Episode) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState()
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    val visible = series != null
    val loading = episodes == Resource.Loading

    LaunchedEffect(series) { currentOnRefresh() }

    LaunchedEffect(episodes is Resource.Success) {
        if (loading) sheetState.partialExpand()
        else sheetState.expand()
    }

    BottomSheet(
        sheetState = sheetState,
        visible = visible,
        blurBody = true,
        header = {
            series?.let {
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                when (episodes) {
                    Resource.Loading -> {
                        CircularProgressIndicator()
                    }

                    else -> {
                        IconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = null,
                            onClick = onRefresh
                        )
                    }
                }
            }
        },
        body = {
            HorizontalDivider()
            when (episodes) {
                is Resource.Failure -> {
                    Text(
                        text = episodes.message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                else -> {}
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                if (loading) {
                    stickyHeader {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(spacing.small)) }
                when (episodes) {
                    is Resource.Success -> {
                        items(episodes.data) { episode ->
                            XtreamEpisodeItem(
                                episode = episode,
                                onClick = { onEpisodeClick(episode) },
                                modifier = Modifier.padding(horizontal = spacing.medium)
                            )
                        }
                    }

                    else -> {}
                }
            }
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

@Composable
private fun XtreamEpisodeItem(
    episode: XtreamChannelInfo.Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    OutlinedCard(
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
        modifier = modifier
    ) {
        ListItem(
            trailingContent = {
                Box(
                    modifier = Modifier
                        .clip(AbsoluteSmoothCornerShape(spacing.medium, 65))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        color = MaterialTheme.colorScheme.onPrimary,
                        text = episode.episodeNum.toString(),
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
            },
            headlineContent = {
                Text(
                    text = episode.title.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            modifier = Modifier.clickable { onClick() }
        )
    }
}