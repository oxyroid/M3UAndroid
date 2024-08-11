package com.m3u.feature.channel

import android.content.pm.ActivityInfo
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotationAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.feature.channel.MaskCenterState.Pause
import com.m3u.feature.channel.MaskCenterState.Play
import com.m3u.feature.channel.MaskCenterState.Replay
import com.m3u.feature.channel.components.MaskTextButton
import com.m3u.feature.channel.components.PlayerMask
import com.m3u.feature.channel.components.VerticalGestureArea
import com.m3u.i18n.R.string
import com.m3u.material.components.mask.MaskButton
import com.m3u.material.components.mask.MaskCircleButton
import com.m3u.material.components.mask.MaskPanel
import com.m3u.material.components.mask.MaskState
import com.m3u.material.effects.currentBackStackEntry
import com.m3u.material.ktx.tv
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.FontFamilies
import com.m3u.ui.Image
import com.m3u.ui.helper.LocalHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
internal fun ChannelMask(
    cover: String,
    title: String,
    playlistTitle: String,
    playerState: PlayerState,
    volume: Float,
    brightness: Float,
    maskState: MaskState,
    favourite: Boolean,
    isSeriesPlaylist: Boolean,
    isPanelExpanded: Boolean,
    hasTrack: Boolean,
    onFavourite: () -> Unit,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    openOrClosePanel: () -> Unit,
    onEnterPipMode: () -> Unit,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val preferences = hiltPreferences()
    val helper = LocalHelper.current
    val spacing = LocalSpacing.current
    val configuration = LocalConfiguration.current
    val tv = tv()
    val coroutineScope = rememberCoroutineScope()

    val onBackPressedDispatcher = checkNotNull(
        LocalOnBackPressedDispatcherOwner.current
    ).onBackPressedDispatcher

    var gesture: MaskGesture? by remember { mutableStateOf(null) }

    // because they will be updated frequently,
    // they must be wrapped with rememberUpdatedState when using them.
    val currentVolume by rememberUpdatedState(volume)
    val currentBrightness by rememberUpdatedState(brightness)

    val muted = currentVolume == 0f

    val defaultBrightnessOrVolumeContentDescription = when {
        muted -> stringResource(string.feat_channel_tooltip_unmute)
        else -> stringResource(string.feat_channel_tooltip_mute)
    }

    val brightnessOrVolumeText by remember {
        derivedStateOf {
            when (gesture) {
                MaskGesture.VOLUME -> "${(currentVolume.coerceIn(0f..1f) * 100).roundToInt()}%"
                MaskGesture.BRIGHTNESS -> "${(currentBrightness.coerceIn(0f..1f) * 100).roundToInt()}%"
                else -> null
            }
        }
    }

    val isProgressEnabled = preferences.slider
    val isStaticAndSeekable by remember(
        playerState.player,
        playerState.playState
    ) {
        derivedStateOf {
            when {
                playerState.player == null -> false
                !playerState.player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) -> false
                else -> with(playerState.player) {
                    !isCurrentMediaItemDynamic && isCurrentMediaItemSeekable
                }
            }
        }
    }

    val contentPosition by produceState(
        initialValue = -1L,
        isStaticAndSeekable,
        isProgressEnabled
    ) {
        while (isProgressEnabled && isStaticAndSeekable) {
            delay(50.milliseconds)
            value = playerState.player?.currentPosition ?: -1L
        }
        value = -1L
    }
    val contentDuration by produceState(
        initialValue = -1L,
        isStaticAndSeekable,
        isProgressEnabled
    ) {
        while (isProgressEnabled && isStaticAndSeekable) {
            delay(50.milliseconds)
            value = playerState.player?.duration?.absoluteValue ?: -1L
        }
        value = -1L
    }

    var bufferedPosition: Long? by remember { mutableStateOf(null) }
    var volumeBeforeMuted: Float by remember { mutableFloatStateOf(1f) }

    val isPanelGestureSupported = configuration.screenWidthDp < configuration.screenHeightDp

    LaunchedEffect(bufferedPosition) {
        bufferedPosition?.let {
            delay(800.milliseconds)
            playerState.player?.seekTo(it)
        }
    }

    LaunchedEffect(playerState.playState) {
        if (playerState.playState == Player.STATE_READY) {
            bufferedPosition = null
        }
    }

    if (tv) {
        BackHandler(maskState.visible && !maskState.locked) {
            maskState.sleep()
        }
    }

    Box {
        MaskPanel(
            state = maskState
        )

        PlayerMask(
            state = maskState,
            header = {
                val backStackEntry by currentBackStackEntry()
                MaskButton(
                    state = maskState,
                    icon = backStackEntry?.navigationIcon ?: Icons.AutoMirrored.Rounded.ArrowBack,
                    onClick = { onBackPressedDispatcher.onBackPressed() },
                    contentDescription = stringResource(string.feat_channel_tooltip_on_back_pressed)
                )
                Spacer(modifier = Modifier.weight(1f))

                MaskTextButton(
                    state = maskState,
                    icon = when (gesture) {
                        MaskGesture.BRIGHTNESS -> when {
                            brightness < 0.5f -> Icons.Rounded.DarkMode
                            else -> Icons.Rounded.LightMode
                        }

                        else -> when {
                            volume == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                            volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                            else -> Icons.AutoMirrored.Rounded.VolumeUp
                        }
                    },
                    text = brightnessOrVolumeText,
                    tint = when (gesture) {
                        null -> if (muted) MaterialTheme.colorScheme.error else Color.Unspecified
                        MaskGesture.VOLUME -> if (muted) MaterialTheme.colorScheme.error else Color.Unspecified
                        MaskGesture.BRIGHTNESS -> Color.Unspecified
                    },
                    onClick = {
                        onVolume(
                            if (volume != 0f) {
                                volumeBeforeMuted = volume
                                0f
                            } else volumeBeforeMuted
                        )
                    },
                    contentDescription = defaultBrightnessOrVolumeContentDescription
                )
                if (!isSeriesPlaylist) {
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.Star,
                        tint = if (favourite) Color(0xffffcd3c) else Color.Unspecified,
                        onClick = onFavourite,
                        contentDescription = if (favourite) stringResource(string.feat_channel_tooltip_unfavourite)
                        else stringResource(string.feat_channel_tooltip_favourite)
                    )
                }

                if (hasTrack) {
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.HighQuality,
                        onClick = openChooseFormat,
                        contentDescription = stringResource(string.feat_channel_tooltip_choose_format)
                    )
                }

                if (!isPanelGestureSupported) {
                    MaskButton(
                        state = maskState,
                        icon = if (isPanelExpanded) Icons.Rounded.Archive
                        else Icons.Rounded.Unarchive,
                        onClick = openOrClosePanel,
                        contentDescription = stringResource(string.feat_channel_tooltip_open_panel)
                    )
                }

                if (!tv && preferences.screencast) {
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.Cast,
                        onClick = openDlnaDevices,
                        contentDescription = stringResource(string.feat_channel_tooltip_cast)
                    )
                }
                if (!tv && playerState.videoSize.isNotEmpty) {
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.PictureInPicture,
                        onClick = onEnterPipMode,
                        contentDescription = stringResource(string.feat_channel_tooltip_enter_pip_mode)
                    )
                }
            },
            body = {
                Box {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)
                    ) {
                        VerticalGestureArea(
                            percent = currentBrightness,
                            onDragStart = {
                                maskState.lock(MaskGesture.BRIGHTNESS)
                                gesture = MaskGesture.BRIGHTNESS
                            },
                            onDragEnd = {
                                maskState.unlock(MaskGesture.BRIGHTNESS, 400.milliseconds)
                                gesture = null
                            },
                            onDrag = onBrightness,
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.18f)
                        )
                        VerticalGestureArea(
                            percent = currentVolume,
                            onDragStart = {
                                maskState.lock(MaskGesture.VOLUME)
                                gesture = MaskGesture.VOLUME
                            },
                            onDragEnd = {
                                maskState.unlock(MaskGesture.VOLUME, 400.milliseconds)
                                gesture = null
                            },
                            onDrag = onVolume,
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.18f)
                        )
                    }
                    val maskCenterState = MaskCenterState.of(
                        playerState.playState,
                        playerState.isPlaying,
                        preferences.alwaysShowReplay,
                        isPanelExpanded,
                        playerState.playerError
                    )
                    MaskCenterButton(
                        maskCenterState = maskCenterState,
                        maskState = maskState,
                        onPlay = { playerState.player?.play() },
                        onPause = { playerState.player?.pause() },
                        onRetry = { coroutineScope.launch { helper.replay() } },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            },
            footer = {
                if (preferences.fullInfoPlayer && cover.isNotEmpty()) {
                    Image(
                        model = cover,
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.Bottom)
                            .clip(RoundedCornerShape(spacing.small))
                    )
                }
                Column(
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .semantics(mergeDescendants = true) { }
                        .weight(1f)
                ) {
                    if (!isPanelExpanded || !isPanelGestureSupported) {
                        Text(
                            text = playlistTitle.trim().uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            color = LocalContentColor.current.copy(0.54f),
                            fontFamily = FontFamilies.LexendExa,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = title.trim(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                    val playStateDisplayText =
                        ChannelMaskUtils.playStateDisplayText(playerState.playState)
                    val exceptionDisplayText =
                        ChannelMaskUtils.playbackExceptionDisplayText(playerState.playerError)

                    if (playStateDisplayText.isNotEmpty()
                        || exceptionDisplayText.isNotEmpty()
                        || (isStaticAndSeekable && isProgressEnabled)
                    ) {
                        Spacer(
                            modifier = Modifier.height(spacing.small)
                        )
                    }
                    if (playStateDisplayText.isNotEmpty()) {
                        Text(
                            text = playStateDisplayText.uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalContentColor.current.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                    if (exceptionDisplayText.isNotBlank()) {
                        Text(
                            text = exceptionDisplayText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
                if (!tv) {
                    val autoRotating by ChannelMaskUtils.IsAutoRotatingEnabled
                    LaunchedEffect(autoRotating) {
                        if (autoRotating) {
                            helper.screenOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }
                    if (preferences.screenRotating && !autoRotating) {
                        MaskButton(
                            state = maskState,
                            icon = Icons.Rounded.ScreenRotationAlt,
                            onClick = {
                                helper.screenOrientation = when (helper.screenOrientation) {
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            },
                            contentDescription = stringResource(string.feat_channel_tooltip_screen_rotating)
                        )
                    }
                }
            },
            slider = {
                when {
                    isProgressEnabled && isStaticAndSeekable -> {
                        val animContentPosition by animateFloatAsState(
                            targetValue = (bufferedPosition
                                ?: contentPosition.coerceAtLeast(0L)).toFloat(),
                            label = "anim-content-position"
                        )
                        val fontWeight by animateIntAsState(
                            targetValue = if (bufferedPosition != null) 800
                            else 400,
                            label = "position-text-font-weight"
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ChannelMaskUtils.timeunitDisplayTest(
                                    (bufferedPosition ?: contentPosition)
                                        .toDuration(DurationUnit.MILLISECONDS)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = 0.75f),
                                maxLines = 1,
                                fontFamily = FontFamilies.JetbrainsMono,
                                fontWeight = FontWeight(fontWeight),
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            var isSliderHasFocus by remember { mutableStateOf(false) }
                            val tvSliderModifier = Modifier
                                .onFocusChanged {
                                    isSliderHasFocus = it.hasFocus
                                    if (it.hasFocus) {
                                        maskState.wake()
                                    }
                                }
                                .focusable()
                                .onKeyEvent { event ->
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            bufferedPosition = (bufferedPosition
                                                ?: contentPosition
                                                    .coerceAtLeast(0L)) - 15000L
                                            maskState.wake()
                                            true
                                        }

                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            bufferedPosition = (bufferedPosition
                                                ?: contentPosition
                                                    .coerceAtLeast(0L)) + 15000L
                                            maskState.wake()
                                            true
                                        }

                                        else -> false
                                    }
                                }
                            val sliderThumbWidthDp by animateDpAsState(
                                targetValue = if (isSliderHasFocus) 8.dp
                                else 4.dp,
                                label = "slider-thumb-width-dp"
                            )
                            val sliderColors = SliderDefaults.colors(
                                thumbColor = if (!isSliderHasFocus) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(0.56f)
                            )
                            val sliderInteractionSource = remember { MutableInteractionSource() }
                            Slider(
                                value = animContentPosition,
                                valueRange = 0f..contentDuration
                                    .coerceAtLeast(0L)
                                    .toFloat(),
                                onValueChange = {
                                    bufferedPosition = it.roundToLong()
                                    maskState.wake()
                                },
                                colors = sliderColors,
                                thumb = {
                                    SliderDefaults.Thumb(
                                        interactionSource = sliderInteractionSource,
                                        colors = sliderColors,
                                        thumbSize = DpSize(sliderThumbWidthDp, 44.dp)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .thenIf(tv) { tvSliderModifier }
                            )
                        }

                    }
                }
            },
            modifier = modifier
        )
    }
}

@Composable
private fun MaskCenterButton(
    maskCenterState: MaskCenterState?,
    maskState: MaskState,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onRetry: () -> Unit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when (maskCenterState) {
            Replay, Play, Pause -> {
                MaskCircleButton(
                    state = maskState,
                    icon = when (maskCenterState) {
                        Replay -> Icons.Rounded.Refresh
                        Play -> Icons.Rounded.PlayArrow
                        Pause -> Icons.Rounded.Pause
                        else -> Icons.Rounded.NewReleases // never reached
                    },
                    onClick = when (maskCenterState) {
                        Replay -> onRetry
                        Play -> onPlay
                        Pause -> onPause
                        else -> {
                            {}
                        } // never reached
                    }
                )
            }

            else -> {}
        }
    }
}

private enum class MaskCenterState {
    Replay, Play, Pause, Loading;

    companion object {
        fun of(
            @Player.State playState: Int,
            isPlaying: Boolean,
            alwaysShowReplay: Boolean,
            isPanelExpanded: Boolean,
            playerError: Exception?
        ): MaskCenterState? = when {
            isPanelExpanded -> null
            playState == Player.STATE_BUFFERING -> Loading
            alwaysShowReplay || playState in arrayOf(
                Player.STATE_IDLE,
                Player.STATE_ENDED
            ) || playerError != null -> Replay

            else -> if (!isPlaying) Play else Pause
        }
    }
}