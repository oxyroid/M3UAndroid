package com.m3u.smartphone.ui.material.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.core.foundation.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.SeriesEpisode
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.ktx.rememberUiBidiFormatter
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
    val bidiFormatter = rememberUiBidiFormatter()

    LaunchedEffect(loading) {
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
                    text = bidiFormatter.natural(it.title),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } ?: Spacer(modifier = Modifier.weight(1f))
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    top = spacing.medium,
                    bottom = spacing.large,
                ),
            ) {
                when (episodes) {
                    is Resource.Failure -> {
                        item {
                            PageStateContent(
                                icon = Icons.Rounded.CloudOff,
                                title = stringResource(string.ui_episodes_load_error_title),
                                description = episodes.message
                                    ?.takeIf(String::isNotBlank)
                                    ?.let(bidiFormatter::natural)
                                    ?: stringResource(
                                        string.ui_episodes_load_error_description
                                    ),
                                actionLabel = stringResource(string.ui_action_retry),
                                actionIcon = Icons.Rounded.Refresh,
                                onAction = onRefresh,
                            )
                        }
                    }

                    is Resource.Success -> {
                        if (episodes.data.isEmpty()) {
                            item {
                                PageStateContent(
                                    icon = Icons.Rounded.VideoLibrary,
                                    title = stringResource(string.ui_episodes_empty_title),
                                    description = stringResource(
                                        string.ui_episodes_empty_description
                                    ),
                                    actionLabel = stringResource(string.ui_action_refresh),
                                    actionIcon = Icons.Rounded.Refresh,
                                    onAction = onRefresh,
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = episodes.data,
                                key = { _, episode -> episode.id },
                            ) { index, episode ->
                                SeriesEpisodeItem(
                                    episode = episode,
                                    first = index == 0,
                                    last = index == episodes.data.lastIndex,
                                    onClick = { onEpisodeClick(episode) },
                                    modifier = Modifier.padding(
                                        horizontal = spacing.medium
                                    ),
                                )
                            }
                        }
                    }

                    Resource.Loading -> {
                        item {
                            Spacer(modifier = Modifier.height(spacing.small))
                        }
                    }
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
    first: Boolean,
    last: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val bidiFormatter = rememberUiBidiFormatter()
    val cornerRadius = 24.dp
    val shape = RoundedCornerShape(
        topStart = cornerRadius.takeIf { first } ?: 0.dp,
        topEnd = cornerRadius.takeIf { first } ?: 0.dp,
        bottomStart = cornerRadius.takeIf { last } ?: 0.dp,
        bottomEnd = cornerRadius.takeIf { last } ?: 0.dp,
    )

    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column {
            ListItem(
                trailingContent = {
                    episode.sequenceLabel?.let { sequenceLabel ->
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = bidiFormatter.ltr(sequenceLabel),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    textDirection = TextDirection.Ltr,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(
                                    horizontal = spacing.medium,
                                    vertical = spacing.extraSmall,
                                )
                            )
                        }
                    }
                },
                headlineContent = {
                    Text(
                        text = bidiFormatter.natural(episode.title),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
            )
            if (!last) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = spacing.medium),
                )
            }
        }
    }
}
