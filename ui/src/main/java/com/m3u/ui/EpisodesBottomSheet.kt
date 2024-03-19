package com.m3u.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.m3u.core.wrapper.Resource
import com.m3u.core.wrapper.takeOrElse
import com.m3u.data.database.model.Stream
import com.m3u.data.parser.xtream.XtreamStreamInfo
import com.m3u.material.components.BottomSheet
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.IconButton
import com.m3u.material.model.LocalSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
fun EpisodesBottomSheet(
    series: Stream?,
    // FIXME: Use shared Episode Class
    episodes: Resource<ImmutableList<XtreamStreamInfo.Episode>>,
    onRefresh: () -> Unit,
    onEpisodeClick: (XtreamStreamInfo.Episode) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState()
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    val actualEpisodes = episodes.takeOrElse { persistentListOf() }
    val visible = series != null
    val loading = episodes == Resource.Loading

    LaunchedEffect(series) { currentOnRefresh() }

    LaunchedEffect(episodes) {
        if (loading) sheetState.partialExpand()
        else sheetState.expand()
    }

    BottomSheet(
        sheetState = sheetState,
        visible = visible,
        header = {
            series?.let {
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Crossfade(
                targetState = episodes,
                label = "episodes-bottom-sheet-status"
            ) { resource ->
                when (resource) {
                    Resource.Loading -> {
                        CircularProgressIndicator()
                    }

                    is Resource.Failure -> {
                        IconButton(
                            icon = Icons.Rounded.Refresh,
                            contentDescription = null,
                            onClick = onRefresh
                        )
                    }

                    is Resource.Success -> {}
                }
            }
        },
        body = {
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
                    .weight(1f)
                    .padding(top = spacing.medium)
            ) {
                if (loading) {
                    stickyHeader {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                items(actualEpisodes) { episode ->
                    XtreamEpisodeItem(
                        episode = episode,
                        onClick = { onEpisodeClick(episode) }
                    )
                }
            }
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

@Composable
private fun XtreamEpisodeItem(
    episode: XtreamStreamInfo.Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ListItem(
        trailingContent = {
            Badge {
                Text(episode.episodeNum.toString())
            }
        },
        headlineContent = {
            Text(
                text = episode.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        modifier = modifier.clickable { onClick() }
    )
}