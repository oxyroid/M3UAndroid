package com.m3u.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.m3u.data.tv.model.keyCode
import com.m3u.i18n.R.string

@Composable
fun App(
    onBackPressed: () -> Unit,
    viewModel: TvHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val currentFavorite by viewModel.currentFavorite.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentProgramme by viewModel.currentProgramme.collectAsStateWithLifecycle()
    val remoteControlCode by viewModel.remoteControlCode.collectAsStateWithLifecycle()
    val subscribingXtream by viewModel.subscribingXtream.collectAsStateWithLifecycle()
    val subscribingM3u by viewModel.subscribingM3u.collectAsStateWithLifecycle()
    val xtreamSubscriptionMessage by viewModel.xtreamSubscriptionMessage.collectAsStateWithLifecycle()
    val m3uSubscriptionMessage by viewModel.m3uSubscriptionMessage.collectAsStateWithLifecycle()
    val view = LocalView.current
    var destination by remember { mutableStateOf(TvDestination.Home) }
    var surface by remember { mutableStateOf(TvSurface.Browse) }
    var focusChannelsOnLibraryOpen by remember { mutableStateOf(false) }
    val closePlayer = {
        viewModel.releasePlayer()
        surface = TvSurface.Browse
    }

    BackHandler {
        if (surface == TvSurface.Player) {
            closePlayer()
        } else {
            onBackPressed()
        }
    }

    LaunchedEffect(view) {
        viewModel.remoteDirections.collect { direction ->
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, direction.keyCode))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, direction.keyCode))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.startupPlaybackRequests.collect {
            surface = TvSurface.Player
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        TvBackdrop(channel = currentChannel ?: state.heroChannel)
        Row(Modifier.fillMaxSize()) {
            TvNavigationRail(
                selected = destination,
                onSelect = {
                    focusChannelsOnLibraryOpen = it == TvDestination.Library
                    destination = it
                }
            )
            TvBrowsePane(
                destination = destination,
                state = state,
                subscribingXtream = subscribingXtream,
                subscribingM3u = subscribingM3u,
                xtreamSubscriptionMessage = xtreamSubscriptionMessage,
                m3uSubscriptionMessage = m3uSubscriptionMessage,
                focusChannelsOnLibraryOpen = focusChannelsOnLibraryOpen,
                onOpenLibrary = {
                    focusChannelsOnLibraryOpen = false
                    destination = TvDestination.Library
                },
                onPlaylist = {
                    viewModel.selectPlaylist(it)
                    focusChannelsOnLibraryOpen = true
                    destination = TvDestination.Library
                },
                onLibraryChannelFocusHandled = { focusChannelsOnLibraryOpen = false },
                onRefresh = viewModel::refreshSelectedPlaylist,
                onAddXtreamPlaylist = viewModel::addXtreamPlaylist,
                onClearXtreamSubscriptionMessage = viewModel::clearXtreamSubscriptionMessage,
                onAddM3uPlaylist = viewModel::addM3uPlaylist,
                onClearM3uSubscriptionMessage = viewModel::clearM3uSubscriptionMessage,
                onPlay = {
                    viewModel.play(it)
                    surface = TvSurface.Player
                },
                onPlayRecent = {
                    viewModel.playRecent()
                    surface = TvSurface.Player
                }
            )
        }

        AnimatedVisibility(
            visible = surface == TvSurface.Player,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TvPlayerScreen(
                player = player,
                channel = currentChannel,
                isFavorite = currentFavorite,
                isPlaying = isPlaying,
                playbackState = playbackState,
                programme = currentProgramme,
                onPlayPause = { viewModel.pauseOrContinue(!isPlaying) },
                onFavorite = viewModel::toggleCurrentFavorite,
                onPreviousChannel = viewModel::playPreviousChannel,
                onNextChannel = viewModel::playNextChannel,
                onBack = closePlayer,
                onClose = closePlayer
            )
        }

        remoteControlCode?.let { code ->
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(TvColors.Surface.copy(alpha = 0.86f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(string.tv_remote_pairing_code),
                    color = TvColors.TextSecondary,
                    fontFamily = TvFonts.Body,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = code.toString().padStart(6, '0'),
                    color = TvColors.TextPrimary,
                    fontFamily = TvFonts.Body,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
