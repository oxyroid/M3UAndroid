package com.m3u.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import coil.compose.AsyncImage
import com.m3u.core.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import com.m3u.i18n.R.string
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

object TvColors {
    val Background = Color(0xFF080B10)
    val Surface = Color(0xFF111821)
    val SurfaceRaised = Color(0xFF1B2530)
    val Focus = Color(0xFF9BE7C7)
    val Coral = Color(0xFFFFB27A)
    val Ink = Color(0xFF07100D)
    val TextPrimary = Color(0xFFF3F7FA)
    val TextSecondary = Color(0xFFB4C0CC)
    val Muted = Color(0xFF728091)
}

private enum class TvDestination(
    val icon: ImageVector
) {
    Home(Icons.Rounded.Home),
    Library(Icons.AutoMirrored.Rounded.PlaylistPlay),
    Favorites(Icons.Rounded.Favorite),
    Settings(Icons.Rounded.Settings)
}

private enum class TvScreen {
    Home,
    Player
}

@Immutable
data class TvUiState(
    val playlists: List<Playlist> = emptyList(),
    val counts: Map<Playlist, Int> = emptyMap(),
    val selectedPlaylist: Playlist? = null,
    val channels: List<Channel> = emptyList(),
    val favorites: List<Channel> = emptyList(),
    val recent: Channel? = null,
    val loadingChannels: Boolean = false
)

@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val playerManager: PlayerManager
) : ViewModel() {
    private val _state = MutableStateFlow(TvUiState())
    val state: StateFlow<TvUiState> = _state.asStateFlow()

    val player = playerManager.player
    val currentChannel = playerManager.channel
    val isPlaying = playerManager.isPlaying
    val playbackState = playerManager.playbackState

    init {
        viewModelScope.launch {
            playlistRepository
                .observeAllCounts()
                .flowOn(Dispatchers.Default)
                .collect { counts ->
                    val playlists = counts.keys
                        .filterNot { it.source == DataSource.EPG }
                        .sortedBy { it.title.lowercase() }
                    val current = _state.value.selectedPlaylist
                    val selected = current
                        ?.let { active -> playlists.firstOrNull { it.url == active.url } }
                        ?: playlists.firstOrNull()
                    _state.update {
                        it.copy(
                            playlists = playlists,
                            counts = counts,
                            selectedPlaylist = selected
                        )
                    }
                    if (selected != null && selected.url != current?.url) {
                        loadChannels(selected.url)
                    }
                }
        }
        viewModelScope.launch {
            channelRepository.observeAllFavorite().collect { favorites ->
                _state.update { it.copy(favorites = favorites) }
            }
        }
        viewModelScope.launch {
            channelRepository.observePlayedRecently().collect { recent ->
                _state.update { it.copy(recent = recent) }
            }
        }
    }

    fun selectPlaylist(playlist: Playlist) {
        if (_state.value.selectedPlaylist?.url == playlist.url) return
        _state.update { it.copy(selectedPlaylist = playlist) }
        loadChannels(playlist.url)
    }

    fun refreshSelectedPlaylist() {
        val playlist = state.value.selectedPlaylist ?: return
        viewModelScope.launch {
            playlistRepository.refresh(playlist.url)
            loadChannels(playlist.url)
        }
    }

    fun play(channel: Channel) {
        viewModelScope.launch {
            playerManager.play(MediaCommand.Common(channel.id))
            channelRepository.reportPlayed(channel.id)
        }
    }

    fun playRecent() {
        state.value.recent?.let(::play)
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(channel.id)
        }
    }

    fun pauseOrContinue(continuePlayback: Boolean) {
        playerManager.pauseOrContinue(continuePlayback)
    }

    fun releasePlayer() {
        playerManager.release()
    }

    private fun loadChannels(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(loadingChannels = true) }
            val channels = channelRepository
                .getByPlaylistUrl(url)
                .filterNot { it.hidden }
                .sortedWith(
                    compareBy<Channel> { it.category.lowercase() }
                        .thenBy { it.title.lowercase() }
                )
            _state.update {
                it.copy(
                    channels = channels,
                    loadingChannels = false
                )
            }
        }
    }
}

@Composable
fun App(
    onBackPressed: () -> Unit,
    viewModel: TvHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    var destination by remember { mutableIntStateOf(TvDestination.Home.ordinal) }
    var screen by remember { mutableIntStateOf(TvScreen.Home.ordinal) }

    BackHandler {
        if (screen == TvScreen.Player.ordinal) {
            screen = TvScreen.Home.ordinal
        } else {
            onBackPressed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TvBackgroundArtwork(
            channel = currentChannel ?: state.recent ?: state.channels.firstOrNull()
        )

        if (state.playlists.isEmpty()) {
            Row(Modifier.fillMaxSize()) {
                TvBrandRail()
                EmptyLibraryScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 44.dp, top = 56.dp, end = 72.dp, bottom = 56.dp)
                )
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                TvNavigationRail(
                    selected = TvDestination.entries[destination],
                    onSelect = { destination = it.ordinal }
                )
                when (TvDestination.entries[destination]) {
                    TvDestination.Home -> TvHomeScreen(
                        state = state,
                        onPlaylist = {
                            destination = TvDestination.Library.ordinal
                            viewModel.selectPlaylist(it)
                        },
                        onPlay = {
                            viewModel.play(it)
                            screen = TvScreen.Player.ordinal
                        },
                        onPlayRecent = {
                            viewModel.playRecent()
                            screen = TvScreen.Player.ordinal
                        },
                        onFavorite = viewModel::toggleFavorite
                    )

                    TvDestination.Library -> TvLibraryScreen(
                        state = state,
                        onPlaylist = viewModel::selectPlaylist,
                        onRefresh = viewModel::refreshSelectedPlaylist,
                        onPlay = {
                            viewModel.play(it)
                            screen = TvScreen.Player.ordinal
                        },
                        onFavorite = viewModel::toggleFavorite
                    )

                    TvDestination.Favorites -> TvFavoritesScreen(
                        state = state,
                        onPlay = {
                            viewModel.play(it)
                            screen = TvScreen.Player.ordinal
                        },
                        onFavorite = viewModel::toggleFavorite
                    )

                    TvDestination.Settings -> TvSettingsScreen(state)
                }
            }
        }

        AnimatedVisibility(
            visible = screen == TvScreen.Player.ordinal,
            enter = fadeIn() + scaleIn(initialScale = 1.02f),
            exit = fadeOut() + scaleOut(targetScale = 1.02f)
        ) {
            TvPlayerScreen(
                player = player,
                channel = currentChannel,
                isPlaying = isPlaying,
                playbackState = playbackState,
                onPlayPause = {
                    viewModel.pauseOrContinue(!isPlaying)
                },
                onBack = {
                    screen = TvScreen.Home.ordinal
                },
                onClose = {
                    viewModel.releasePlayer()
                    screen = TvScreen.Home.ordinal
                }
            )
        }
    }

    if (screen == TvScreen.Player.ordinal) {
        BackHandler { screen = TvScreen.Home.ordinal }
    }

}

@Composable
private fun TvBackgroundArtwork(channel: Channel?) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = channel?.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to TvColors.Background,
                        0.55f to TvColors.Background.copy(alpha = 0.82f),
                        1f to TvColors.Background.copy(alpha = 0.48f)
                    )
                )
        )
    }
}

@Composable
private fun TvNavigationRail(
    selected: TvDestination,
    onSelect: (TvDestination) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(104.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.34f))
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(TvColors.Focus)
        ) {
            androidx.tv.material3.Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = TvColors.Ink,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        TvDestination.entries.forEach { destination ->
            RailButton(
                destination = destination,
                selected = destination == selected,
                onClick = { onSelect(destination) }
            )
        }
    }
}

@Composable
private fun TvBrandRail() {
    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(104.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.34f))
            .padding(top = 88.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(TvColors.Focus)
        ) {
            androidx.tv.material3.Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = TvColors.Ink,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun RailButton(
    destination: TvDestination,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(62.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                when {
                    selected -> TvColors.Focus
                    focused -> TvColors.SurfaceRaised
                    else -> Color.Transparent
                }
            )
            .border(
                width = if (focused && !selected) 2.dp else 0.dp,
                color = if (focused && !selected) TvColors.Coral else Color.Transparent,
                shape = RoundedCornerShape(18.dp)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
    ) {
        androidx.tv.material3.Icon(
            imageVector = destination.icon,
            contentDescription = destination.name,
            tint = if (selected) TvColors.Ink else TvColors.TextSecondary,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun TvHomeScreen(
    state: TvUiState,
    onPlaylist: (Playlist) -> Unit,
    onPlay: (Channel) -> Unit,
    onPlayRecent: () -> Unit,
    onFavorite: (Channel) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(28.dp),
        contentPadding = PaddingValues(start = 28.dp, top = 48.dp, end = 48.dp, bottom = 56.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            HeroPanel(
                state = state,
                onPlayRecent = onPlayRecent
            )
        }
        item {
            SectionHeader(
                title = stringResource(string.tv_section_playlists),
                subtitle = stringResource(string.tv_section_playlists_hint)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(top = 14.dp)
            ) {
                items(state.playlists) { playlist ->
                    PlaylistTile(
                        playlist = playlist,
                        count = state.counts[playlist] ?: 0,
                        onClick = { onPlaylist(playlist) }
                    )
                }
            }
        }
        item {
            SectionHeader(
                title = stringResource(string.tv_section_recent_channels),
                subtitle = stringResource(string.tv_section_recent_channels_hint)
            )
            ChannelShelf(
                channels = state.channels.take(18),
                favorites = state.favorites,
                onPlay = onPlay,
                onFavorite = onFavorite
            )
        }
    }
}

@Composable
private fun TvLibraryScreen(
    state: TvUiState,
    onPlaylist: (Playlist) -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, top = 46.dp, end = 48.dp, bottom = 48.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(292.dp)
        ) {
            SectionHeader(
                title = stringResource(string.tv_library_title),
                subtitle = stringResource(string.tv_library_subtitle)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                items(state.playlists) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        count = state.counts[playlist] ?: 0,
                        selected = playlist == state.selectedPlaylist,
                        onClick = { onPlaylist(playlist) }
                    )
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    androidx.tv.material3.Text(
                        text = state.selectedPlaylist?.title?.title().orEmpty(),
                        color = TvColors.TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.tv.material3.Text(
                        text = stringResource(
                            string.tv_channel_count,
                            state.channels.size
                        ),
                        color = TvColors.TextSecondary,
                        fontSize = 14.sp
                    )
                }
                FocusButton(
                    text = stringResource(string.feat_setting_label_subscribe),
                    icon = Icons.Rounded.Refresh,
                    onClick = onRefresh
                )
            }
            ChannelGrid(
                channels = state.channels,
                favorites = state.favorites,
                onPlay = onPlay,
                onFavorite = onFavorite
            )
        }
    }
}

@Composable
private fun TvFavoritesScreen(
    state: TvUiState,
    onPlay: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, top = 48.dp, end = 48.dp, bottom = 48.dp)
    ) {
        SectionHeader(
            title = stringResource(string.tv_favorites_title),
            subtitle = stringResource(string.tv_favorites_subtitle)
        )
        ChannelGrid(
            channels = state.favorites,
            favorites = state.favorites,
            onPlay = onPlay,
            onFavorite = onFavorite
        )
    }
}

@Composable
private fun TvSettingsScreen(state: TvUiState) {
    Column(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 28.dp, top = 48.dp, end = 48.dp, bottom = 48.dp)
    ) {
        SectionHeader(
            title = stringResource(string.tv_settings_title),
            subtitle = stringResource(string.tv_settings_subtitle)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard(
                title = stringResource(string.tv_metric_playlists),
                value = state.playlists.size.toString(),
                icon = Icons.Rounded.VideoLibrary
            )
            MetricCard(
                title = stringResource(string.tv_metric_channels),
                value = state.counts.values.sum().toString(),
                icon = Icons.AutoMirrored.Rounded.PlaylistPlay
            )
            MetricCard(
                title = stringResource(string.tv_metric_favorites),
                value = state.favorites.size.toString(),
                icon = Icons.Rounded.Favorite
            )
        }
    }
}

@Composable
private fun HeroPanel(
    state: TvUiState,
    onPlayRecent: () -> Unit
) {
    val recent = state.recent
    Row(
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .height(270.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            androidx.tv.material3.Text(
                text = stringResource(string.tv_home_title),
                color = TvColors.TextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            androidx.tv.material3.Text(
                text = stringResource(string.tv_home_subtitle),
                color = TvColors.TextSecondary,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth(0.78f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                FocusButton(
                    text = if (recent == null) {
                        stringResource(string.tv_action_open_library)
                    } else {
                        stringResource(string.tv_action_resume)
                    },
                    icon = Icons.Rounded.PlayArrow,
                    enabled = recent != null,
                    onClick = onPlayRecent
                )
                InfoPill(
                    text = stringResource(
                        string.tv_library_summary,
                        state.playlists.size,
                        state.counts.values.sum()
                    )
                )
            }
        }
        PosterFrame(
            channel = recent ?: state.channels.firstOrNull(),
            modifier = Modifier
                .width(380.dp)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.tv.material3.Text(
            text = title,
            color = TvColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
        androidx.tv.material3.Text(
            text = subtitle,
            color = TvColors.TextSecondary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ChannelShelf(
    channels: List<Channel>,
    favorites: List<Channel>,
    onPlay: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 14.dp)
    ) {
        items(channels) { channel ->
            ChannelCard(
                channel = channel,
                favorite = favorites.any { it.id == channel.id },
                onPlay = { onPlay(channel) },
                onFavorite = { onFavorite(channel) },
                modifier = Modifier.width(220.dp)
            )
        }
    }
}

@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    favorites: List<Channel>,
    onPlay: (Channel) -> Unit,
    onFavorite: (Channel) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(204.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(bottom = 28.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(channels, key = { it.id }) { channel ->
            ChannelCard(
                channel = channel,
                favorite = favorites.any { it.id == channel.id },
                onPlay = { onPlay(channel) },
                onFavorite = { onFavorite(channel) }
            )
        }
    }
}

@Composable
private fun ChannelCard(
    channel: Channel,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusSurface(
        onClick = onPlay,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp)
        ) {
            Box {
                PosterImage(
                    model = channel.cover,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(8.dp))
                )
                IconButtonChip(
                    icon = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.Star,
                    selected = favorite,
                    onClick = onFavorite,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            androidx.tv.material3.Text(
                text = channel.title.title(),
                color = TvColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            androidx.tv.material3.Text(
                text = channel.category.ifBlank { stringResource(string.feat_playlist_scheme_unknown) },
                color = TvColors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlaylistTile(
    playlist: Playlist,
    count: Int,
    onClick: () -> Unit
) {
    FocusSurface(
        onClick = onClick,
        modifier = Modifier
            .width(254.dp)
            .height(142.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TvColors.Focus)
            ) {
                androidx.tv.material3.Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    contentDescription = null,
                    tint = TvColors.Ink
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.tv.material3.Text(
                    text = playlist.title.title(),
                    color = TvColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.tv.material3.Text(
                    text = playlistLabel(playlist, count),
                    color = TvColors.TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    FocusSurface(
        onClick = onClick,
        selected = selected,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            androidx.tv.material3.Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                contentDescription = null,
                tint = if (selected) TvColors.Ink else TvColors.Focus,
                modifier = Modifier.size(28.dp)
            )
            Column(Modifier.weight(1f)) {
                androidx.tv.material3.Text(
                    text = playlist.title.title(),
                    color = if (selected) TvColors.Ink else TvColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.tv.material3.Text(
                    text = playlistLabel(playlist, count),
                    color = if (selected) TvColors.Ink.copy(alpha = 0.74f) else TvColors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun playlistLabel(playlist: Playlist, count: Int): String {
    val type = when {
        playlist.isSeries -> stringResource(string.tv_playlist_type_series)
        playlist.isVod -> stringResource(string.tv_playlist_type_vod)
        else -> playlist.source.value.uppercase()
    }
    return stringResource(string.tv_playlist_label, type, count)
}

@Composable
private fun PosterFrame(
    channel: Channel?,
    modifier: Modifier = Modifier
) {
    FocusSurface(
        onClick = {},
        modifier = modifier,
        enabled = false
    ) {
        Box(Modifier.fillMaxSize()) {
            PosterImage(
                model = channel?.cover,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f)
                        )
                    )
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    androidx.tv.material3.Text(
                        text = channel?.title?.title() ?: stringResource(string.tv_empty_recent_title),
                        color = TvColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    androidx.tv.material3.Text(
                        text = channel?.category ?: stringResource(string.tv_empty_recent_subtitle),
                        color = TvColors.TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterImage(
    model: String?,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.background(TvColors.SurfaceRaised)
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        if (model.isNullOrBlank()) {
            androidx.tv.material3.Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = null,
                tint = TvColors.Muted,
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    var focused by remember { androidx.compose.runtime.mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    selected -> TvColors.Focus
                    focused -> TvColors.SurfaceRaised
                    else -> TvColors.Surface.copy(alpha = 0.82f)
                }
            )
            .border(
                BorderStroke(
                    width = if (focused) 3.dp else 1.dp,
                    color = when {
                        selected -> TvColors.Focus
                        focused -> TvColors.Coral
                        else -> Color.White.copy(alpha = 0.08f)
                    }
                ),
                shape = shape
            )
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (enabled) {
                    Modifier
                        .clickable(onClick = onClick)
                        .focusable()
                } else {
                    Modifier
                }
            )
    ) {
        content()
    }
}

@Composable
private fun FocusButton(
    text: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FocusSurface(
        onClick = onClick,
        enabled = enabled,
        selected = enabled,
        modifier = Modifier.height(48.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp)
        ) {
            androidx.tv.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) TvColors.Ink else TvColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            androidx.tv.material3.Text(
                text = text,
                color = if (enabled) TvColors.Ink else TvColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun IconButtonChip(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (selected) TvColors.Coral else Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = onClick)
            .focusable()
    ) {
        androidx.tv.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) TvColors.Ink else TvColors.TextPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun InfoPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 16.dp)
    ) {
        androidx.tv.material3.Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = TvColors.TextSecondary,
            modifier = Modifier.size(19.dp)
        )
        androidx.tv.material3.Text(
            text = text,
            color = TvColors.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector
) {
    FocusSurface(
        onClick = {},
        enabled = false,
        modifier = Modifier
            .width(220.dp)
            .height(132.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            androidx.tv.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvColors.Focus,
                modifier = Modifier.size(28.dp)
            )
            Column {
                androidx.tv.material3.Text(
                    text = value,
                    color = TvColors.TextPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                androidx.tv.material3.Text(
                    text = title,
                    color = TvColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun TvPlayerScreen(
    player: Player?,
    channel: Channel?,
    isPlaying: Boolean,
    playbackState: Int,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onBack)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (player != null) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(188.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(48.dp)
        ) {
            FocusButton(
                text = if (isPlaying) {
                    stringResource(string.tv_action_pause)
                } else {
                    stringResource(string.tv_action_play)
                },
                icon = Icons.Rounded.PlayArrow,
                onClick = onPlayPause
            )
            FocusButton(
                text = stringResource(string.tv_action_close_player),
                icon = Icons.Rounded.Tv,
                onClick = onClose
            )
            Column(Modifier.padding(start = 12.dp)) {
                androidx.tv.material3.Text(
                    text = channel?.title?.title().orEmpty(),
                    color = TvColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                androidx.tv.material3.Text(
                    text = playerStateText(playbackState),
                    color = TvColors.TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun playerStateText(playbackState: Int): String = when (playbackState) {
    Player.STATE_BUFFERING -> stringResource(string.feat_channel_playback_state_buffering)
    Player.STATE_READY -> stringResource(string.feat_channel_playback_state_ready)
    Player.STATE_ENDED -> stringResource(string.feat_channel_playback_state_ended)
    else -> stringResource(string.feat_channel_playback_state_idle)
}

@Composable
private fun EmptyLibraryScreen(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            androidx.tv.material3.Text(
                text = stringResource(string.tv_home_title),
                color = TvColors.TextPrimary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            androidx.tv.material3.Text(
                text = stringResource(string.tv_empty_library_title),
                color = TvColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold
            )
            androidx.tv.material3.Text(
                text = stringResource(string.tv_empty_library_subtitle),
                color = TvColors.TextSecondary,
                fontSize = 17.sp,
                lineHeight = 25.sp,
                modifier = Modifier.fillMaxWidth(0.82f)
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(0.76f)
            ) {
                InfoPill(
                    text = stringResource(string.tv_empty_library_phone_hint),
                    modifier = Modifier.fillMaxWidth()
                )
                InfoPill(
                    text = stringResource(string.tv_empty_library_restore_hint),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        EmptySetupPanel()
    }
}

@Composable
private fun EmptySetupPanel() {
        FocusSurface(
            onClick = {},
            enabled = false,
            modifier = Modifier
                .width(382.dp)
                .height(312.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            0f to TvColors.SurfaceRaised,
                            1f to TvColors.Surface
                        )
                    )
                    .padding(26.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TvColors.Focus)
                    ) {
                        androidx.tv.material3.Icon(
                            imageVector = Icons.Rounded.VideoLibrary,
                            contentDescription = null,
                            tint = TvColors.Ink,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        androidx.tv.material3.Text(
                            text = stringResource(string.tv_empty_library_panel_title),
                            color = TvColors.TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        androidx.tv.material3.Text(
                            text = stringResource(string.tv_empty_library_panel_subtitle),
                            color = TvColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SetupStep(
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        text = stringResource(string.tv_empty_library_step_sources)
                    )
                    SetupStep(
                        icon = Icons.Rounded.Refresh,
                        text = stringResource(string.tv_empty_library_step_sync)
                    )
                    SetupStep(
                        icon = Icons.Rounded.Tv,
                        text = stringResource(string.tv_empty_library_step_watch)
                    )
                }
            }
        }
}

@Composable
private fun SetupStep(
    icon: ImageVector,
    text: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            androidx.tv.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvColors.Focus,
                modifier = Modifier.size(18.dp)
            )
        }
        androidx.tv.material3.Text(
            text = text,
            color = TvColors.TextSecondary,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
