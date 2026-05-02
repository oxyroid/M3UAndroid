package com.m3u.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    var destination by remember { mutableStateOf(TvDestination.Home) }
    var surface by remember { mutableStateOf(TvSurface.Browse) }
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
                onSelect = { destination = it }
            )
            TvBrowsePane(
                destination = destination,
                state = state,
                onOpenLibrary = { destination = TvDestination.Library },
                onPlaylist = {
                    viewModel.selectPlaylist(it)
                    destination = TvDestination.Library
                },
                onRefresh = viewModel::refreshSelectedPlaylist,
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
                isPlaying = isPlaying,
                playbackState = playbackState,
                onPlayPause = { viewModel.pauseOrContinue(!isPlaying) },
                onBack = closePlayer,
                onClose = closePlayer
            )
        }
    }
}