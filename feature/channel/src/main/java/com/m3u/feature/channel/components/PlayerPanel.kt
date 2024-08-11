package com.m3u.feature.channel.components

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.tv.material3.surfaceColorAtElevation
import coil.compose.SubcomposeAsyncImage
import com.m3u.core.util.collections.indexOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Episode
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.service.MediaCommand
import com.m3u.material.components.Background
import com.m3u.material.components.CircularProgressIndicator
import com.m3u.material.components.IconButton
import com.m3u.material.effects.BackStackEntry
import com.m3u.material.effects.BackStackHandler
import com.m3u.material.ktx.Edge
import com.m3u.material.ktx.blurEdges
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.material.shape.AbsoluteSmoothCornerShape
import com.m3u.ui.FontFamilies
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.util.TimeUtils.formatEOrSh
import com.m3u.ui.util.TimeUtils.toEOrSh
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.CardDefaults as TvCardDefaults
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.Text as TvText

@Composable
internal fun PlayerPanel(
    title: String,
    playlistTitle: String,
    channelId: Int,
    isChannelsSupported: Boolean,
    isProgrammeSupported: Boolean,
    isPanelExpanded: Boolean,
    channels: LazyPagingItems<Channel>,
    programmes: LazyPagingItems<Programme>,
    programmeRange: ProgrammeRange,
    modifier: Modifier = Modifier,
    programmeReminderIds: List<Int>,
    onRemindProgramme: (Programme) -> Unit,
    onCancelRemindProgramme: (Programme) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current

    val useVertical = configuration.screenWidthDp < configuration.screenHeightDp
    Background(
        shape = if (useVertical) RectangleShape else AbsoluteSmoothCornerShape(
            cornerRadiusTL = spacing.medium,
            cornerRadiusBL = spacing.medium
        ),
        modifier = modifier
    ) {
        var programme: Programme? by remember { mutableStateOf(null) }
        var animProgramme: Programme? by remember { mutableStateOf(null) }

        PlayerPanelImpl(
            title = title,
            playlistTitle = playlistTitle,
            channelId = channelId,
            isChannelsSupported = isChannelsSupported,
            isProgrammeSupported = isProgrammeSupported,
            isPanelExpanded = isPanelExpanded,
            channels = channels,
            programmes = programmes,
            programmeRange = programmeRange,
            programmeReminderIds = programmeReminderIds,
            onProgrammePressed = {
                programme = it
                animProgramme = it
            }
        )
        LaunchedEffect(isPanelExpanded) {
            if (!isPanelExpanded) {
                programme = null
            }
        }
        AnimatedVisibility(
            visible = programme != null,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.clickable(
                interactionSource = null,
                indication = null,
                role = Role.Image,
                onClick = { programme = null }
            )
        ) {
            val currentProgramme = animProgramme
            if (currentProgramme != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = AbsoluteRoundedCornerShape(4.dp),
                    modifier = Modifier.padding(spacing.medium)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        SubcomposeAsyncImage(
                            model = currentProgramme.icon,
                            contentDescription = currentProgramme.title,
                            contentScale = ContentScale.Crop,
                            loading = {
                                // coil will measure the loading content same with the parent's modifier.
                                Box {
                                    CircularProgressIndicator(
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16 / 9f)
                        )

                        val start = Instant.fromEpochMilliseconds(currentProgramme.start)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .toEOrSh()
                        val end = Instant.fromEpochMilliseconds(currentProgramme.end)
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .toEOrSh()
                        Column(
                            modifier = Modifier.padding(spacing.medium - spacing.small),
                            verticalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Row {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(spacing.small),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${start.formatEOrSh(false)} - ${
                                            end.formatEOrSh(false)
                                        }",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LocalContentColor.current.copy(0.65f),
                                        fontFamily = FontFamilies.LexendExa
                                    )
                                    Text(
                                        text = currentProgramme.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontFamily = FontFamilies.LexendExa
                                    )
                                }
                                val isReminderShowing = Clock.System.now()
                                    .toEpochMilliseconds() < currentProgramme.start
                                if (isReminderShowing) {
                                    val inReminder = currentProgramme.id in programmeReminderIds
                                    IconButton(
                                        icon = if (!inReminder) Icons.Outlined.Notifications
                                        else Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        onClick = {
                                            if (inReminder) onCancelRemindProgramme(currentProgramme)
                                            else onRemindProgramme(currentProgramme)
                                        },
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }
                            }

                            Column(
                                Modifier
                                    .blurEdges(
                                        MaterialTheme.colorScheme.surface,
                                        listOf(Edge.Top, Edge.Bottom)
                                    )
                                    .verticalScroll(rememberScrollState())
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(vertical = spacing.small)
                            ) {
                                Text(
                                    text = currentProgramme.description,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamilies.LexendExa
                                )
                            }
                        }
                    }
                }
            }
        }
        BackStackHandler(
            entry = BackStackEntry(Icons.Rounded.Close),
            enabled = programme != null
        ) {
            programme = null
        }
    }
}

@Composable
fun PlayerPanelImpl(
    title: String,
    playlistTitle: String,
    channelId: Int,
    isChannelsSupported: Boolean,
    isProgrammeSupported: Boolean,
    isPanelExpanded: Boolean,
    channels: LazyPagingItems<Channel>,
    programmes: LazyPagingItems<Programme>,
    programmeRange: ProgrammeRange,
    programmeReminderIds: List<Int>,
    modifier: Modifier = Modifier,
    onProgrammePressed: (Programme) -> Unit
) {
    val configuration = LocalConfiguration.current
    val spacing = LocalSpacing.current

    val useVertical = configuration.screenWidthDp < configuration.screenHeightDp
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        modifier = modifier
            .fillMaxSize()
            .thenIf(!useVertical) {
                Modifier.statusBarsPadding()
            }
            .padding(vertical = spacing.medium)
    ) {
        if (isPanelExpanded && useVertical) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.medium)
            ) {
                Text(
                    text = title.trim(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = playlistTitle.trim().uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    color = LocalContentColor.current.copy(0.54f),
                    fontFamily = FontFamilies.LexendExa,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        if (isChannelsSupported) {
            ChannelGallery(
                // TODO
                value = ChannelGalleryValue.PagingChannel(channels, channelId),
                isPanelExpanded = isPanelExpanded,
                vertical = !isProgrammeSupported
            )
        }

        if (isProgrammeSupported) {
            ProgramGuide(
                isPanelExpanded = isPanelExpanded,
                programmes = programmes,
                range = programmeRange,
                programmeReminderIds = programmeReminderIds,
                onProgrammePressed = onProgrammePressed,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelGallery(
    value: ChannelGalleryValue,
    isPanelExpanded: Boolean,
    modifier: Modifier = Modifier,
    vertical: Boolean = false
) {
    val spacing = LocalSpacing.current
    val lazyListState = rememberLazyListState()
    val tv = tv()

    ScrollToCurrentEffect(
        value = value,
        isPanelExpanded = isPanelExpanded,
        lazyListState = lazyListState
    )

    val content: LazyListScope.() -> Unit = {
        when (value) {
            is ChannelGalleryValue.PagingChannel -> {
                val channels = value.channels
                val channelId = value.channelId
                items(channels.itemCount) { i ->
                    channels[i]?.let { channel ->
                        val isPlaying = channel.id == channelId
                        ChannelGalleryItem(
                            channel = channel,
                            isPlaying = isPlaying
                        )
                    }
                }
            }

            is ChannelGalleryValue.XtreamEpisode -> {
                items(value.episodes) { series ->
                    // TODO
                }
            }
        }
    }
    if (!vertical) {
        LazyRow(
            state = lazyListState,
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            contentPadding = PaddingValues(spacing.medium),
            modifier = modifier,
            content = content
        )
    } else {
        val focusManager = LocalFocusManager.current
        LazyColumn(
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            contentPadding = PaddingValues(spacing.medium),
            modifier = modifier
                .fillMaxWidth()
                .thenIf(tv) {
                    Modifier.onKeyEvent {
                        when (it.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                focusManager.moveFocus(FocusDirection.Exit)
                                true
                            }

                            else -> false
                        }
                    }
                },
            content = content
        )
    }
}

@Immutable
private sealed class ChannelGalleryValue {
    data class PagingChannel(
        val channels: LazyPagingItems<Channel>,
        val channelId: Int
    ) : ChannelGalleryValue()

    data class XtreamEpisode(
        val episodes: List<Episode>,
        val seriesId: Int
    ) : ChannelGalleryValue()
}

@Composable
private fun ChannelGalleryItem(
    channel: Channel,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()
    val tv = tv()

    if (!tv) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (!isPlaying)
                    MaterialTheme.colorScheme.surfaceColorAtElevation(spacing.medium)
                else MaterialTheme.colorScheme.onSurface,
                contentColor = if (!isPlaying) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceColorAtElevation(spacing.small)
            ),
            shape = AbsoluteRoundedCornerShape(spacing.medium),
            elevation = CardDefaults.cardElevation(spacing.none),
            onClick = {
                if (isPlaying) return@Card
                coroutineScope.launch {
                    helper.play(
                        MediaCommand.Common(channel.id)
                    )
                }
            },
            modifier = modifier
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold.takeIf { isPlaying },
                modifier = Modifier.padding(spacing.medium)
            )
        }
    } else {
        TvCard(
            colors = TvCardDefaults.colors(
                containerColor = if (!isPlaying)
                    TvMaterialTheme.colorScheme.surfaceColorAtElevation(spacing.medium)
                else TvMaterialTheme.colorScheme.onSurface,
                contentColor = if (!isPlaying) TvMaterialTheme.colorScheme.onSurface
                else TvMaterialTheme.colorScheme.surfaceColorAtElevation(spacing.small)
            ),
            onClick = {
                if (isPlaying) return@TvCard
                coroutineScope.launch {
                    helper.play(
                        MediaCommand.Common(channel.id)
                    )
                }
            },
            modifier = modifier
        ) {
            TvText(
                text = channel.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold.takeIf { isPlaying },
                modifier = Modifier.padding(spacing.medium)
            )
        }
    }
}

@Composable
private fun ScrollToCurrentEffect(
    value: ChannelGalleryValue,
    isPanelExpanded: Boolean,
    lazyListState: LazyListState,
    scrollOffset: Int = -120
) {
    if (isPanelExpanded) {
        when (value) {
            is ChannelGalleryValue.PagingChannel -> {
                val channels = value.channels
                val channelId = value.channelId
                LaunchedEffect(channels.itemCount) {
                    var index = -1
                    for (i in 0 until channels.itemCount) {
                        if (channels[i]?.id == channelId) {
                            index = i
                            break
                        }
                    }
                    if (index != -1) {
                        lazyListState.animateScrollToItem(index, scrollOffset)
                    }
                }
            }

            is ChannelGalleryValue.XtreamEpisode -> {
                val episodes = value.episodes
                val seriesId = value.seriesId
                LaunchedEffect(episodes.size) {
                    val index = episodes.indexOf { it.id == seriesId }
                    if (index != -1) {
                        lazyListState.animateScrollToItem(index, scrollOffset)
                    }
                }
            }
        }
    }
}
