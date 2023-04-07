package com.m3u.features.live

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.Divider
import androidx.compose.material.LocalContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.m3u.core.annotation.ClipMode
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.core.util.context.toast
import com.m3u.ui.components.Background
import com.m3u.ui.components.ExoPlayer
import com.m3u.ui.components.Image
import com.m3u.ui.components.Mask
import com.m3u.ui.components.MaskButton
import com.m3u.ui.components.MaskCircleButton
import com.m3u.ui.components.MaskPanel
import com.m3u.ui.components.MaskState
import com.m3u.ui.components.rememberMaskState
import com.m3u.ui.components.rememberPlayerState
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme
import com.m3u.ui.util.EventHandler
import com.m3u.ui.util.LifecycleEffect
import kotlin.math.absoluteValue

@Composable
internal fun LiveRoute(
    init: LiveEvent.Init,
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val helper = LocalHelper.current
    val state: LiveState by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEffect { event ->
        when (event) {
            Lifecycle.Event.ON_CREATE -> {
                helper.hideSystemUI()
            }

            Lifecycle.Event.ON_DESTROY -> {
                helper.showSystemUI()
            }

            else -> {}
        }
    }

    EventHandler(state.message) {
        context.toast(it)
    }

    LaunchedEffect(init) {
        viewModel.onEvent(init)
    }
    LiveScreen(
        init = state.init,
        experimentalMode = state.experimentalMode,
        clipMode = state.clipMode,
        recording = state.recording,
        searchDlnaDevices = { viewModel.onEvent(LiveEvent.SearchDlnaDevices) },
        onRecord = { viewModel.onEvent(LiveEvent.Record) },
        onBackPressed = onBackPressed,
        player = state.player,
        playback = state.playerState.playback,
        videoSize = state.playerState.videoSize,
        playerError = state.playerState.playerError,
        onInstallMedia = { viewModel.onEvent(LiveEvent.InstallMedia(it)) },
        onUninstallMedia = { viewModel.onEvent(LiveEvent.UninstallMedia) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveScreen(
    init: LiveState.Init,
    @ClipMode clipMode: Int,
    recording: Boolean,
    searchDlnaDevices: () -> Unit,
    onRecord: () -> Unit,
    onBackPressed: () -> Unit,
    experimentalMode: Boolean,
    player: Player?,
    playback: @Player.State Int,
    videoSize: Rect,
    playerError: PlaybackException?,
    onInstallMedia: (String) -> Unit,
    onUninstallMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalTheme.current
    when (init) {
        is LiveState.InitSpecial -> {
            LivePart(
                player = player,
                playback = playback,
                videoSize = videoSize,
                playerError = playerError,
                title = init.live?.title.orEmpty(),
                url = init.live?.url.orEmpty(),
                cover = init.live?.cover.orEmpty(),
                feedTitle = init.feed?.title.orEmpty(),
                experimentalMode = experimentalMode,
                clipMode = clipMode,
                recording = recording,
                onRecord = onRecord,
                searchDlnaDevices = searchDlnaDevices,
                onBackPressed = onBackPressed,
                onInstallMedia = onInstallMedia,
                onUninstallMedia = onUninstallMedia,
                modifier = modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .testTag("features:live")
            )
        }

        is LiveState.InitPlayList -> {
            val pagerState = rememberPagerState(init.initialIndex)
            VerticalPager(
                state = pagerState,
                pageCount = init.lives.size,
                modifier = modifier
                    .fillMaxSize()
                    .background(theme.background)
                    .testTag("features:live")
            ) { page ->
                LivePart(
                    player = player,
                    playback = playback,
                    videoSize = videoSize,
                    playerError = playerError,
                    title = init.lives[page].title,
                    feedTitle = init.feed?.title.orEmpty(),
                    url = init.lives[page].url,
                    cover = init.lives[page].cover.orEmpty(),
                    experimentalMode = experimentalMode,
                    clipMode = clipMode,
                    recording = recording,
                    onRecord = onRecord,
                    searchDlnaDevices = searchDlnaDevices,
                    onBackPressed = onBackPressed,
                    onInstallMedia = onInstallMedia,
                    onUninstallMedia = onUninstallMedia,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .graphicsLayer {
                            val offset = (pagerState.currentPage - page) +
                                    pagerState.currentPageOffsetFraction.absoluteValue
                                        .coerceIn(0f, 1f)
                            val scale = lerp(1f, 0.8f, offset)
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun LivePart(
    player: Player?,
    playback: @Player.State Int,
    videoSize: Rect,
    playerError: PlaybackException?,
    title: String,
    feedTitle: String,
    url: String,
    cover: String,
    experimentalMode: Boolean,
    @ClipMode clipMode: Int,
    recording: Boolean,
    onRecord: () -> Unit,
    searchDlnaDevices: () -> Unit,
    onBackPressed: () -> Unit,
    onInstallMedia: (String) -> Unit,
    onUninstallMedia: () -> Unit,
    modifier: Modifier = Modifier
) {
    Background(
        color = Color.Black,
        contentColor = Color.White
    ) {
        Box(
            modifier = modifier
        ) {
            val helper = LocalHelper.current
            val state = rememberPlayerState(
                player = player,
                url = url,
                clipMode = clipMode,
                onInstallMedia = onInstallMedia,
                onUninstallMedia = onUninstallMedia
            )

            ExoPlayer(
                state = state,
                modifier = Modifier.fillMaxSize()
            )
            val maskState = rememberMaskState { visible ->
                helper.systemUiVisibility = visible
            }
            val shouldShowPlaceholder =
                cover.isNotEmpty() && (playback != Player.STATE_READY || videoSize.isEmpty)
            AnimatedVisibility(
                visible = shouldShowPlaceholder,
                enter = scaleIn(initialScale = 0.7f) + fadeIn(),
                exit = scaleOut(targetScale = 0.7f) + fadeOut()
            ) {
                Image(
                    model = cover,
                    modifier = Modifier.fillMaxSize()
                )
            }
            LiveMask(
                state = maskState,
                header = {
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.ArrowBack,
                        onClick = onBackPressed
                    )
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                    if (experimentalMode) {
                        MaskButton(
                            state = maskState,
                            icon = if (recording) Icons.Rounded.RadioButtonChecked
                            else Icons.Rounded.RadioButtonUnchecked,
                            tint = if (recording) LocalTheme.current.error
                            else LocalContentColor.current.copy(alpha = LocalContentAlpha.current),
                            onClick = onRecord
                        )
                        val shouldShowCastButton = (playback != Player.STATE_IDLE)
                        if (shouldShowCastButton) {
                            MaskButton(
                                state = maskState,
                                icon = Icons.Rounded.Cast,
                                onClick = searchDlnaDevices
                            )
                        }
                    }
                    val shouldShowPipButton = videoSize.isNotEmpty
                    if (shouldShowPipButton) {
                        MaskButton(
                            state = maskState,
                            icon = Icons.Rounded.PictureInPicture,
                            onClick = {
                                helper.enterPipMode(videoSize)
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
                            onInstallMedia(state.url)
                        }
                    )
                },
                foot = {
                    Column(
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = feedTitle,
                            style = MaterialTheme.typography.subtitle1
                        )
                        Text(
                            text = title,
                            style = MaterialTheme.typography.h5,
                            fontWeight = FontWeight.ExtraBold
                        )
                        val playbackDisplayText = playback.displayText
                        val exceptionDisplayText = playerError.displayText
                        if (playbackDisplayText.isNotEmpty() || exceptionDisplayText.isNotEmpty()) {
                            Divider(
                                modifier = Modifier.height(LocalSpacing.current.small)
                            )
                        }
                        AnimatedVisibility(playbackDisplayText.isNotEmpty()) {
                            Text(
                                text = playbackDisplayText,
                                style = MaterialTheme.typography.subtitle2,
                                color = LocalContentColor.current.copy(alpha = 0.75f)
                            )
                        }
                        AnimatedVisibility(exceptionDisplayText.isNotEmpty()) {
                            Text(
                                text = exceptionDisplayText,
                                style = MaterialTheme.typography.subtitle2,
                                color = LocalTheme.current.error
                            )
                        }
                    }
                }
            )
            LaunchedEffect(playerError) {
                if (playerError != null) {
                    maskState.keepAlive()
                }
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
