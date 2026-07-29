package com.m3u.smartphone.ui.material.components

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.core.foundation.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.SeriesEpisode
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
fun EpisodesBottomSheet(
    series: Channel?,
    episodes: Resource<List<SeriesEpisode>>,
    onRefresh: () -> Unit,
    onEpisodeClick: (SeriesEpisode) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState()

    val visible = series != null
    val loading = episodes == Resource.Loading

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
                            onClick = onRefresh
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = stringResource(string.ui_action_refresh),
                            )
                        }
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
                            SeriesEpisodeItem(
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
private fun SeriesEpisodeItem(
    episode: SeriesEpisode,
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
                episode.sequenceLabel?.let { sequenceLabel ->
                    Box(
                        modifier = Modifier
                            .clip(AbsoluteSmoothCornerShape(spacing.medium, 65))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            color = MaterialTheme.colorScheme.onPrimary,
                            text = sequenceLabel,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDirection = TextDirection.Ltr,
                            ),
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
            },
            headlineContent = {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.clickable { onClick() }
        )
    }
}
