package com.m3u.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
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
    viewModel: TvHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val remoteControlCode by viewModel.remoteControlCode.collectAsStateWithLifecycle()
    val view = LocalView.current
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    var destination by remember { mutableStateOf(TvDestination.Home) }
    var surface by remember { mutableStateOf(TvSurface.Browse) }
    val navigationFocusRequesters = remember {
        TvDestination.entries.associateWith { FocusRequester() }
    }
    val closePlayer = {
        viewModel.releasePlayer()
        surface = TvSurface.Browse
    }

    BackHandler(enabled = surface == TvSurface.Player) {
        closePlayer()
    }

    LaunchedEffect(view) {
        viewModel.remoteDirections.collect { direction ->
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, direction.keyCode))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, direction.keyCode))
        }
    }
    LaunchedEffect(viewModel, localeTag) {
        viewModel.updateLocale(localeTag)
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
                onSelect = { destination = it },
                focusRequesters = navigationFocusRequesters,
            )
            TvBrowsePane(
                destination = destination,
                navigationFocusRequester = navigationFocusRequesters.getValue(destination),
                state = state,
                onOpenLibrary = { destination = TvDestination.Library },
                onPlaylist = {
                    viewModel.selectPlaylist(it)
                    destination = TvDestination.Library
                },
                onRefresh = viewModel::refreshSelectedPlaylist,
                onPlay = {
                    if (viewModel.play(it)) {
                        surface = TvSurface.Player
                    }
                },
                onPlayRecent = {
                    if (viewModel.playRecent()) {
                        surface = TvSurface.Player
                    }
                },
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

        remoteControlCode?.let { code ->
            val displayCode = code.toString().padStart(6, '0')
            val spokenCode = displayCode.toCharArray().joinToString(separator = " ")
            val pairingCodeDescription =
                stringResource(string.ui_remote_control_pairing_code, spokenCode)
            Text(
                text = displayCode,
                color = TvColors.TextPrimary,
                fontFamily = TvFonts.Body,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(TvColors.Surface.copy(alpha = 0.86f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .clearAndSetSemantics {
                        contentDescription = pairingCodeDescription
                        liveRegion = LiveRegionMode.Polite
                    }
            )
        }
    }
}
