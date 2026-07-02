package com.m3u.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R.string
import kotlinx.coroutines.yield

@Composable
fun TvBrowsePane(
    destination: TvDestination,
    state: TvUiState,
    subscribingXtream: Boolean,
    subscribingM3u: Boolean,
    xtreamSubscriptionMessage: TvXtreamSubscriptionMessage?,
    m3uSubscriptionMessage: TvM3uSubscriptionMessage?,
    focusChannelsOnLibraryOpen: Boolean,
    onOpenLibrary: () -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onLibraryChannelFocusHandled: () -> Unit,
    onRefresh: () -> Unit,
    onAddXtreamPlaylist: (String, String, String, String, String?) -> Unit,
    onClearXtreamSubscriptionMessage: () -> Unit,
    onAddM3uPlaylist: (String, String) -> Unit,
    onClearM3uSubscriptionMessage: () -> Unit,
    onPlay: (Channel) -> Unit,
    onPlayRecent: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (state.playlists.isEmpty()) {
            EmptyLibraryScreen(
                subscribingXtream = subscribingXtream,
                subscribingM3u = subscribingM3u,
                xtreamSubscriptionMessage = xtreamSubscriptionMessage,
                m3uSubscriptionMessage = m3uSubscriptionMessage,
                onAddXtreamPlaylist = onAddXtreamPlaylist,
                onClearXtreamSubscriptionMessage = onClearXtreamSubscriptionMessage,
                onAddM3uPlaylist = onAddM3uPlaylist,
                onClearM3uSubscriptionMessage = onClearM3uSubscriptionMessage
            )
        } else {
            when (destination) {
                TvDestination.Home -> HomeScreen(
                    state = state,
                    onOpenLibrary = onOpenLibrary,
                    onPlaylist = onPlaylist,
                    onPlay = onPlay,
                    onPlayRecent = onPlayRecent
                )

                TvDestination.Library -> LibraryScreen(
                    state = state,
                    focusChannelsOnOpen = focusChannelsOnLibraryOpen,
                    onPlaylist = onPlaylist,
                    onChannelFocusRequestHandled = onLibraryChannelFocusHandled,
                    onRefresh = onRefresh,
                    onPlay = onPlay
                )

                TvDestination.Favorites -> ChannelGridScreen(
                    title = stringResource(string.tv_favorites_title),
                    subtitle = stringResource(string.tv_favorites_subtitle),
                    channels = state.favorites,
                    onPlay = onPlay
                )

                TvDestination.Status -> StatusScreen(
                    state = state,
                    subscribingXtream = subscribingXtream,
                    subscribingM3u = subscribingM3u,
                    xtreamSubscriptionMessage = xtreamSubscriptionMessage,
                    m3uSubscriptionMessage = m3uSubscriptionMessage,
                    onAddXtreamPlaylist = onAddXtreamPlaylist,
                    onClearXtreamSubscriptionMessage = onClearXtreamSubscriptionMessage,
                    onAddM3uPlaylist = onAddM3uPlaylist,
                    onClearM3uSubscriptionMessage = onClearM3uSubscriptionMessage
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: TvUiState,
    onOpenLibrary: () -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onPlay: (Channel) -> Unit,
    onPlayRecent: () -> Unit
) {
    val featuredChannels = remember(state.recent, state.channels) {
        (listOfNotNull(state.recent) + state.channels)
            .distinctBy { it.id }
            .take(10)
    }
    var highlightedChannel by remember { mutableStateOf<Channel?>(null) }
    val activeChannel = highlightedChannel ?: featuredChannels.firstOrNull() ?: state.heroChannel
    val heroFocusRequester = remember { FocusRequester() }
    val firstFeaturedFocusRequester = remember { FocusRequester() }
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        yield()
        if (!initialFocusRequested) {
            heroFocusRequester.requestFocus()
            initialFocusRequested = true
        }
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 24.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
    ) {
        item {
            FeaturedCarouselPane(
                state = state,
                channel = activeChannel,
                primaryFocusRequester = heroFocusRequester,
                nextFocusRequester = firstFeaturedFocusRequester,
                onOpenLibrary = onOpenLibrary,
                onPlayRecent = onPlayRecent,
                onPlay = onPlay
            )
        }
        if (featuredChannels.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionTitle(
                        title = stringResource(string.tv_section_recent_channels),
                        subtitle = stringResource(string.tv_section_recent_channels_hint),
                        modifier = Modifier.padding(start = 48.dp)
                    )
                    ContentRow(
                        channels = featuredChannels,
                        onPlay = onPlay,
                        onFocused = { highlightedChannel = it },
                        firstItemFocusRequester = firstFeaturedFocusRequester,
                        recentChannelId = state.recent?.id,
                        recentBadgeText = stringResource(string.tv_action_resume)
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle(
                    title = stringResource(string.tv_section_playlists),
                    subtitle = stringResource(string.tv_section_playlists_hint),
                    modifier = Modifier.padding(start = 48.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 48.dp, top = 16.dp, end = 48.dp, bottom = 8.dp),
                    modifier = Modifier.focusGroup()
                ) {
                    items(state.playlists, key = { it.url }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            count = state.counts[playlist] ?: 0,
                            selected = playlist == state.selectedPlaylist,
                            onClick = { onPlaylist(playlist) },
                            modifier = Modifier
                                .widthIn(min = 256.dp, max = 336.dp)
                                .height(144.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedCarouselPane(
    state: TvUiState,
    channel: Channel?,
    primaryFocusRequester: FocusRequester,
    nextFocusRequester: FocusRequester,
    onOpenLibrary: () -> Unit,
    onPlayRecent: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    val primaryHeroAction = {
        if (channel == null) {
            onOpenLibrary()
        } else if (channel == state.recent) {
            onPlayRecent()
        } else {
            onPlay(channel)
        }
    }
    var selectedAction by remember(channel?.id) { mutableStateOf(HeroAction.Primary) }
    val secondaryAvailable = channel != null
    val selectedHeroAction = if (secondaryAvailable) selectedAction else HeroAction.Primary

    FocusFrame(
        onClick = {
            when (selectedHeroAction) {
                HeroAction.Primary -> primaryHeroAction()
                HeroAction.Secondary -> onOpenLibrary()
            }
        },
        shape = RoundedCornerShape(16.dp),
        focusRequester = primaryFocusRequester,
        focusedScale = 1f,
        focusedBorderWidth = 0.dp,
        focusedBorderColor = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (channel == null) 280.dp else 288.dp)
            .focusProperties { down = nextFocusRequester },
        onKey = { event ->
            if (event.type != KeyEventType.KeyDown || !secondaryAvailable) {
                false
            } else {
                when (event.key) {
                    Key.DirectionLeft -> {
                        selectedAction = HeroAction.Primary
                        true
                    }
                    Key.DirectionRight -> {
                        selectedAction = HeroAction.Secondary
                        true
                    }
                    else -> false
                }
            }
        }
    ) { heroFocused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TvColors.BackgroundSoft)
        ) {
            if (channel != null) {
                PosterArt(
                    model = channel.cover,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.36f))
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.92f),
                            0.48f to Color.Black.copy(alpha = 0.72f),
                            1f to Color.Transparent
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 48.dp, top = 32.dp, end = 48.dp, bottom = 32.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(0.54f)
                ) {
                    Text(
                        text = channel?.title?.title() ?: stringResource(string.tv_home_title),
                        color = TvColors.TextPrimary,
                        fontSize = 38.sp,
                        lineHeight = 42.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = channel?.category?.takeIf { it.isNotBlank() }
                            ?: stringResource(string.tv_home_subtitle),
                        color = TvColors.TextSecondary,
                        fontSize = 17.sp,
                        lineHeight = 25.sp,
                        fontFamily = TvFonts.Body,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        if (channel == null) {
                            HeroActionChip(
                                text = stringResource(string.tv_action_open_library),
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                selected = heroFocused,
                                expanded = heroFocused
                            )
                        } else {
                            HeroActionChip(
                                text = stringResource(string.tv_action_resume),
                                icon = Icons.Rounded.PlayArrow,
                                selected = heroFocused && selectedHeroAction == HeroAction.Primary,
                                expanded = heroFocused && selectedHeroAction == HeroAction.Primary
                            )
                            HeroActionChip(
                                text = stringResource(string.tv_action_open_library),
                                icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                selected = heroFocused && selectedHeroAction == HeroAction.Secondary,
                                expanded = heroFocused && selectedHeroAction == HeroAction.Secondary
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class HeroAction {
    Primary,
    Secondary
}

@Composable
private fun HeroActionChip(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (expanded) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) TvColors.Focus else TvColors.Surface.copy(alpha = 0.86f))
            .border(
                BorderStroke(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) Color.White else Color.White.copy(alpha = 0.08f)
                ),
                RoundedCornerShape(24.dp)
            )
            .padding(horizontal = if (expanded) 16.dp else 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) TvColors.OnFocus else TvColors.TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        if (expanded) {
            Text(
                text = text,
                color = if (selected) TvColors.OnFocus else TvColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = TvFonts.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    state: TvUiState,
    focusChannelsOnOpen: Boolean,
    onPlaylist: (Playlist) -> Unit,
    onChannelFocusRequestHandled: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Channel) -> Unit
) {
    val playlistFocusRequester = remember { FocusRequester() }
    val channelGridFocusRequester = remember { FocusRequester() }
    val focusTarget = state.selectedPlaylist ?: state.playlists.firstOrNull()
    var initialFocusRequested by remember { mutableStateOf(false) }

    LaunchedEffect(focusTarget?.url) {
        if (focusTarget != null && !initialFocusRequested) {
            yield()
            playlistFocusRequester.requestFocus()
            initialFocusRequested = true
        }
    }
    LaunchedEffect(focusChannelsOnOpen, state.loadingChannels, state.channels.size) {
        if (!focusChannelsOnOpen || state.loadingChannels) return@LaunchedEffect
        yield()
        channelGridFocusRequester.requestFocus()
        onChannelFocusRequestHandled()
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
    ) {
        item {
            SectionTitle(
                title = stringResource(string.tv_library_title),
                subtitle = stringResource(string.tv_library_subtitle)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 8.dp),
                modifier = Modifier.focusGroup()
            ) {
                items(state.playlists, key = { it.url }) { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        count = state.counts[playlist] ?: 0,
                        selected = playlist == state.selectedPlaylist,
                        onClick = { onPlaylist(playlist) },
                        focusRequester = if (playlist.url == focusTarget?.url) playlistFocusRequester else null,
                        modifier = Modifier
                            .widthIn(min = 256.dp, max = 336.dp)
                            .height(144.dp)
                            .focusProperties { down = channelGridFocusRequester }
                    )
                }
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = state.selectedPlaylist?.title?.title().orEmpty(),
                        color = TvColors.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = TvFonts.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(string.tv_channel_count, state.channels.size),
                        color = TvColors.TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = TvFonts.Body,
                        maxLines = 1
                    )
                }
                TvActionButton(
                    text = stringResource(string.feat_setting_label_subscribe),
                    icon = Icons.Rounded.Refresh,
                    onClick = onRefresh,
                    modifier = Modifier.focusProperties {
                        up = playlistFocusRequester
                        down = channelGridFocusRequester
                    }
                )
            }
        }

        item {
            if (state.channels.isEmpty()) {
                EmptyChannelGrid(
                    title = stringResource(string.tv_empty_channels_title),
                    subtitle = stringResource(string.tv_empty_channels_subtitle),
                    icon = Icons.Rounded.VideoLibrary,
                    focusRequester = channelGridFocusRequester,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                )
            } else {
                ChannelGrid(
                    channels = state.channels,
                    onPlay = onPlay,
                    firstItemFocusRequester = channelGridFocusRequester,
                    modifier = Modifier.height(620.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelGridScreen(
    title: String,
    subtitle: String,
    channels: List<Channel>,
    onPlay: (Channel) -> Unit
) {
    val firstChannelFocusRequester = remember { FocusRequester() }
    val emptyStateFocusRequester = remember { FocusRequester() }

    LaunchedEffect(channels.size) {
        yield()
        if (channels.isNotEmpty()) {
            firstChannelFocusRequester.requestFocus()
        } else {
            emptyStateFocusRequester.requestFocus()
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp)
            .focusGroup()
    ) {
        SectionTitle(title = title, subtitle = subtitle)
        if (channels.isEmpty()) {
            EmptyChannelGrid(
                title = title,
                subtitle = subtitle,
                focusRequester = emptyStateFocusRequester
            )
        } else {
            ChannelGrid(
                channels = channels,
                onPlay = onPlay,
                firstItemFocusRequester = firstChannelFocusRequester
            )
        }
    }
}

@Composable
private fun EmptyChannelGrid(
    title: String,
    subtitle: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(220.dp),
    icon: ImageVector = Icons.Rounded.Favorite
) {
    FocusFrame(
        onClick = {},
        focusRequester = focusRequester,
        focusedScale = 1f,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) { focused ->
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                modifier = Modifier.size(48.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = title,
                    color = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TvFonts.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = if (focused) TvColors.OnFocus.copy(alpha = 0.78f) else TvColors.TextSecondary,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontFamily = TvFonts.Body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusScreen(
    state: TvUiState,
    subscribingXtream: Boolean,
    subscribingM3u: Boolean,
    xtreamSubscriptionMessage: TvXtreamSubscriptionMessage?,
    m3uSubscriptionMessage: TvM3uSubscriptionMessage?,
    onAddXtreamPlaylist: (String, String, String, String, String?) -> Unit,
    onClearXtreamSubscriptionMessage: () -> Unit,
    onAddM3uPlaylist: (String, String) -> Unit,
    onClearM3uSubscriptionMessage: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 48.dp, end = 64.dp, bottom = 48.dp),
        modifier = Modifier
            .fillMaxSize()
            .focusGroup()
    ) {
        item {
            SectionTitle(
                title = stringResource(string.tv_settings_title),
                subtitle = stringResource(string.tv_settings_subtitle)
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                MetricTile(
                    title = stringResource(string.tv_metric_playlists),
                    value = state.playlists.size.toString(),
                    icon = Icons.Rounded.VideoLibrary,
                    modifier = Modifier
                        .weight(1f)
                        .height(136.dp)
                )
                MetricTile(
                    title = stringResource(string.tv_metric_channels),
                    value = state.channelCount.toString(),
                    icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    modifier = Modifier
                        .weight(1f)
                        .height(136.dp)
                )
                MetricTile(
                    title = stringResource(string.tv_metric_favorites),
                    value = state.favorites.size.toString(),
                    icon = Icons.Rounded.Favorite,
                    modifier = Modifier
                        .weight(1f)
                        .height(136.dp)
                )
            }
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                M3uSubscribePanel(
                    subscribing = subscribingM3u,
                    message = m3uSubscriptionMessage,
                    onSubmit = onAddM3uPlaylist,
                    onInputChange = onClearM3uSubscriptionMessage,
                    modifier = Modifier
                        .weight(0.92f)
                        .height(360.dp)
                )
                XtreamSubscribePanel(
                    subscribing = subscribingXtream,
                    message = xtreamSubscriptionMessage,
                    onSubmit = onAddXtreamPlaylist,
                    onInputChange = onClearXtreamSubscriptionMessage,
                    modifier = Modifier
                        .weight(1.08f)
                        .height(460.dp)
                )
            }
        }
    }
}

@Composable
private fun ContentRow(
    channels: List<Channel>,
    onPlay: (Channel) -> Unit,
    onFocused: (Channel) -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
    recentChannelId: Int? = null,
    recentBadgeText: String? = null
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 48.dp, top = 8.dp, end = 48.dp, bottom = 8.dp),
        modifier = Modifier.focusGroup()
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            ChannelCard(
                channel = channel,
                onPlay = { onPlay(channel) },
                onFocused = { onFocused(channel) },
                focusRequester = firstItemFocusRequester.takeIf { index == 0 },
                compact = true,
                badgeText = recentBadgeText.takeIf { channel.id == recentChannelId },
                modifier = Modifier
                    .widthIn(min = 104.dp, max = 120.dp)
                    .aspectRatio(2f / 3f)
            )
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    onPlay: (Channel) -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    firstItemFocusRequester: FocusRequester? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(168.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp),
        modifier = modifier.focusGroup()
    ) {
        itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
            ChannelCard(
                channel = channel,
                onPlay = { onPlay(channel) },
                focusRequester = firstItemFocusRequester.takeIf { index == 0 }
            )
        }
    }
}

@Composable
private fun EmptyLibraryScreen(
    subscribingXtream: Boolean,
    subscribingM3u: Boolean,
    xtreamSubscriptionMessage: TvXtreamSubscriptionMessage?,
    m3uSubscriptionMessage: TvM3uSubscriptionMessage?,
    onAddXtreamPlaylist: (String, String, String, String, String?) -> Unit,
    onClearXtreamSubscriptionMessage: () -> Unit,
    onAddM3uPlaylist: (String, String) -> Unit,
    onClearM3uSubscriptionMessage: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(string.tv_home_title),
                color = TvColors.TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvFonts.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(string.tv_empty_library_title),
                color = TvColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = TvFonts.Body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(string.tv_empty_library_subtitle),
                color = TvColors.TextSecondary,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                fontFamily = TvFonts.Body,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.82f)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .widthIn(max = 420.dp)
            ) {
                InfoPill(text = stringResource(string.tv_empty_library_xtream_hint), modifier = Modifier.fillMaxWidth())
                InfoPill(text = stringResource(string.tv_empty_library_restore_hint), modifier = Modifier.fillMaxWidth())
            }
        }
        M3uSubscribePanel(
            subscribing = subscribingM3u,
            message = m3uSubscriptionMessage,
            onSubmit = onAddM3uPlaylist,
            onInputChange = onClearM3uSubscriptionMessage,
            modifier = Modifier
                .weight(0.72f)
                .widthIn(max = 360.dp)
                .height(320.dp)
        )
        XtreamSubscribePanel(
            subscribing = subscribingXtream,
            message = xtreamSubscriptionMessage,
            onSubmit = onAddXtreamPlaylist,
            onInputChange = onClearXtreamSubscriptionMessage,
            modifier = Modifier
                .weight(0.88f)
                .widthIn(max = 420.dp)
                .fillMaxHeight()
        )
    }
}

private enum class TvXtreamContentType(val type: String?) {
    All(null),
    Live(DataSource.Xtream.TYPE_LIVE),
    Vod(DataSource.Xtream.TYPE_VOD),
    Series(DataSource.Xtream.TYPE_SERIES)
}

@Composable
private fun M3uSubscribePanel(
    subscribing: Boolean,
    message: TvM3uSubscriptionMessage?,
    onSubmit: (String, String) -> Unit,
    onInputChange: () -> Unit,
    firstInputFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable { mutableStateOf("") }
    var urlOrPath by rememberSaveable { mutableStateOf("") }
    val canSubmit = title.isNotBlank() && urlOrPath.isNotBlank()

    FocusFrame(
        onClick = {},
        enabled = false,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to TvColors.SurfaceRaised,
                        1f to TvColors.BackgroundSoft
                    )
                )
                .padding(20.dp)
        ) {
            SectionTitle(
                title = stringResource(string.tv_m3u_panel_title),
                subtitle = stringResource(string.tv_m3u_panel_subtitle)
            )
            TvInputField(
                value = title,
                onValueChange = {
                    title = it
                    onInputChange()
                },
                placeholder = stringResource(string.tv_m3u_title_placeholder),
                focusRequester = firstInputFocusRequester
            )
            TvInputField(
                value = urlOrPath,
                onValueChange = {
                    urlOrPath = it
                    onInputChange()
                },
                placeholder = stringResource(string.tv_m3u_url_placeholder),
                keyboardType = KeyboardType.Uri
            )
            Text(
                text = message?.let { m3uSubscriptionMessageText(it) }.orEmpty(),
                color = when (message) {
                    TvM3uSubscriptionMessage.Enqueued -> TvColors.Focus
                    TvM3uSubscriptionMessage.MissingFields -> TvColors.Accent
                    null -> Color.Transparent
                },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFamily = TvFonts.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(22.dp)
            )
            TvActionButton(
                text = if (subscribing) {
                    stringResource(string.tv_xtream_subscribing)
                } else {
                    stringResource(string.feat_setting_label_subscribe)
                },
                icon = Icons.Rounded.Add,
                onClick = { onSubmit(title, urlOrPath) },
                enabled = canSubmit && !subscribing,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun XtreamSubscribePanel(
    subscribing: Boolean,
    message: TvXtreamSubscriptionMessage?,
    onSubmit: (String, String, String, String, String?) -> Unit,
    onInputChange: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by rememberSaveable { mutableStateOf("") }
    var basicUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var selectedType by rememberSaveable { mutableStateOf(TvXtreamContentType.All) }
    val canSubmit = title.isNotBlank() &&
        basicUrl.isNotBlank() &&
        username.isNotBlank() &&
        password.isNotBlank()

    FocusFrame(
        onClick = {},
        enabled = false,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to TvColors.SurfaceRaised,
                        1f to TvColors.BackgroundSoft
                    )
                )
                .padding(24.dp)
        ) {
            SectionTitle(
                title = stringResource(string.tv_xtream_panel_title),
                subtitle = stringResource(string.tv_xtream_panel_subtitle)
            )
            TvInputField(
                value = title,
                onValueChange = {
                    title = it
                    onInputChange()
                },
                placeholder = stringResource(string.tv_xtream_title_placeholder)
            )
            TvInputField(
                value = basicUrl,
                onValueChange = {
                    basicUrl = it
                    onInputChange()
                },
                placeholder = stringResource(string.tv_xtream_address_placeholder),
                keyboardType = KeyboardType.Uri
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvInputField(
                    value = username,
                    onValueChange = {
                        username = it
                        onInputChange()
                    },
                    placeholder = stringResource(string.tv_xtream_username_placeholder),
                    modifier = Modifier.weight(1f)
                )
                TvInputField(
                    value = password,
                    onValueChange = {
                        password = it
                        onInputChange()
                    },
                    placeholder = stringResource(string.tv_xtream_password_placeholder),
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvXtreamContentType.entries.forEach { type ->
                    XtreamTypeChip(
                        type = type,
                        selected = type == selectedType,
                        onSelect = {
                            selectedType = type
                            onInputChange()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    )
                }
            }
            val messageText = message?.let { xtreamSubscriptionMessageText(it) }
            Text(
                text = messageText.orEmpty(),
                color = when (message) {
                    TvXtreamSubscriptionMessage.Enqueued -> TvColors.Focus
                    TvXtreamSubscriptionMessage.InvalidUrl,
                    TvXtreamSubscriptionMessage.MissingFields -> TvColors.Accent
                    null -> Color.Transparent
                },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontFamily = TvFonts.Body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(30.dp)
            )
            TvActionButton(
                text = if (subscribing) {
                    stringResource(string.tv_xtream_subscribing)
                } else {
                    stringResource(string.tv_xtream_subscribe)
                },
                icon = Icons.Rounded.Add,
                onClick = {
                    onSubmit(title, basicUrl, username, password, selectedType.type)
                },
                enabled = canSubmit && !subscribing,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun TvInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            color = TvColors.TextPrimary,
            fontSize = 15.sp,
            fontFamily = TvFonts.Body
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(TvColors.Focus),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = TvColors.TextMuted,
                        fontSize = 15.sp,
                        fontFamily = TvFonts.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(TvColors.Surface.copy(alpha = 0.92f))
            .border(
                BorderStroke(
                    width = if (focused) 3.dp else 1.dp,
                    color = if (focused) TvColors.Focus else Color.White.copy(alpha = 0.12f)
                ),
                RoundedCornerShape(8.dp)
            )
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = 13.dp)
    )
}

@Composable
private fun XtreamTypeChip(
    type: TvXtreamContentType,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusFrame(
        onClick = onSelect,
        selected = selected,
        focusedScale = 1f,
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
    ) { focused ->
        Text(
            text = when (type) {
                TvXtreamContentType.All -> stringResource(string.tv_xtream_type_all)
                TvXtreamContentType.Live -> stringResource(string.tv_xtream_type_live)
                TvXtreamContentType.Vod -> stringResource(string.tv_xtream_type_vod)
                TvXtreamContentType.Series -> stringResource(string.tv_xtream_type_series)
            },
            color = if (focused || selected) TvColors.OnFocus else TvColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = TvFonts.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun xtreamSubscriptionMessageText(message: TvXtreamSubscriptionMessage): String =
    when (message) {
        TvXtreamSubscriptionMessage.MissingFields ->
            stringResource(string.tv_xtream_message_missing_fields)
        TvXtreamSubscriptionMessage.InvalidUrl ->
            stringResource(string.tv_xtream_message_invalid_url)
        TvXtreamSubscriptionMessage.Enqueued ->
            stringResource(string.tv_xtream_message_enqueued)
    }

@Composable
private fun m3uSubscriptionMessageText(message: TvM3uSubscriptionMessage): String =
    when (message) {
        TvM3uSubscriptionMessage.MissingFields ->
            stringResource(string.tv_xtream_message_missing_fields)
        TvM3uSubscriptionMessage.Enqueued ->
            stringResource(string.tv_xtream_message_enqueued)
    }

@Composable
private fun SetupPanel(modifier: Modifier = Modifier) {
    FocusFrame(
        onClick = {},
        enabled = false,
        modifier = Modifier
            .then(modifier)
            .aspectRatio(1.18f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to TvColors.SurfaceRaised,
                        1f to TvColors.BackgroundSoft
                    )
                )
                .padding(24.dp)
        ) {
            SectionTitle(
                title = stringResource(string.tv_empty_library_panel_title),
                subtitle = stringResource(string.tv_empty_library_panel_subtitle)
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupStep(text = stringResource(string.tv_empty_library_step_sources))
                SetupStep(text = stringResource(string.tv_empty_library_step_sync))
                SetupStep(text = stringResource(string.tv_empty_library_step_watch))
            }
        }
    }
}

@Composable
private fun SetupStep(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(TvColors.Focus)
        )
        Text(
            text = text,
            color = TvColors.TextSecondary,
            fontSize = 14.sp,
            fontFamily = TvFonts.Body,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
