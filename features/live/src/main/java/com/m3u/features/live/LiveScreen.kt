package com.m3u.features.live

import android.graphics.Rect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.LocalContentColor
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.m3u.core.util.context.toast
import com.m3u.ui.components.*
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.model.LocalUtils
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect

@Composable
internal fun LiveRoute(
    id: Int,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val utils = LocalUtils.current
    val systemUiController = rememberSystemUiController()
    val state: LiveState by viewModel.state.collectAsStateWithLifecycle()
    val theme = LocalTheme.current

    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                utils.hideSystemUI()
                systemUiController.setSystemBarsColor(Color.Black)
            }
            Lifecycle.Event.ON_PAUSE -> {
                utils.showSystemUI()
                systemUiController.setSystemBarsColor(Color.Transparent, theme.isDarkText)
            }
            else -> {}
        }
    }

    EventHandler(state.message) {
        context.toast(it)
    }

    LaunchedEffect(id) {
        viewModel.onEvent(LiveEvent.Init(id))
    }
    LiveScreen(
        modifier = modifier,
        url = state.live?.url,
        searchDlnaDevices = { viewModel.onEvent(LiveEvent.SearchDlnaDevices) },
    )
}

@Composable
private fun LiveScreen(
    url: String?,
    searchDlnaDevices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val utils = LocalUtils.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("features:live")
    ) {
        val videoSize = remember { mutableStateOf(Rect()) }
        val playerState = rememberPlayerState(
            url = url.orEmpty(),
            videoSize = videoSize
        )
        ExoPlayer(
            state = playerState,
            modifier = Modifier.fillMaxSize()
        )
        val maskState = rememberMaskState { visible ->
            when (visible) {
                true -> utils.showSystemUI()
                false -> utils.hideSystemUI()
            }
        }
        val playback by playerState.playbackState
        val exception by playerState.exception

        LiveMask(
            state = maskState,
            header = {
                MaskButton(
                    state = maskState,
                    icon = Icons.Rounded.RadioButtonChecked,
                    onClick = { /*TODO*/ }
                )
                val shouldShowCastButton = (playback != Player.STATE_IDLE)
                if (shouldShowCastButton) {
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.Cast,
                        onClick = searchDlnaDevices
                    )
                }
                val shouldShowPipButton = (!videoSize.value.isEmpty)
                if (shouldShowPipButton) {
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.PictureInPicture,
                        onClick = {
                            utils.enterPipMode(videoSize.value)
                            maskState.sleep()
                        }
                    )
                }
            },
            body = {
                MaskCircleButton(
                    state = maskState,
                    icon = Icons.Rounded.Refresh,
                    onClick = {
                        playerState.setMedia()
                    }
                )
            },
            foot = {
                Column {
                    Text(
                        text = playback.displayText,
                        fontWeight = FontWeight.Bold
                    )
                    val displayText = exception.displayText
                    if (displayText.isNotEmpty()) {
                        Text(
                            text = displayText,
                            color = LocalTheme.current.error
                        )
                    }
                }
            }
        )

        LaunchedEffect(exception) {
            if (exception != null) {
                maskState.keepAlive()
            }
        }
    }
}

@Composable
private fun LiveMask(
    state: MaskState,
    header: @Composable RowScope.() -> Unit,
    body: @Composable RowScope.() -> Unit,
    foot: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    CompositionLocalProvider(
        LocalContentColor provides Color.White
    ) {
        MaskPanel(
            state = state,
            modifier = modifier
        )
        Mask(
            state = state,
            backgroundColor = Color.Black.copy(alpha = 0.54f),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.End,
                content = header
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                content = body
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
                content = foot
            )
        }
    }
}

private val PlaybackException?.displayText: String
    @Composable get() = when (this) {
        null -> ""
        else -> "[$errorCode] $errorCodeName"
    }

private val @Player.State Int.displayText: String
    @Composable get() = when (this) {
        Player.STATE_IDLE -> R.string.playback_state_idle
        Player.STATE_BUFFERING -> R.string.playback_state_buffering
        Player.STATE_READY -> null
        Player.STATE_ENDED -> R.string.playback_state_ended
        else -> null
    }
        ?.let { stringResource(it) }
        .orEmpty()
