package com.m3u.features.stream.fragments

import android.content.pm.ActivityInfo
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotationAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_GET_CURRENT_MEDIA_ITEM
import com.m3u.core.architecture.pref.LocalPref
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.features.stream.StreamState
import com.m3u.features.stream.components.CoverPlaceholder
import com.m3u.features.stream.components.PlayerMask
import com.m3u.features.stream.fragments.StreamFragmentDefaults.detectVerticalMaskGestures
import com.m3u.features.stream.fragments.StreamFragmentDefaults.playStateDisplayText
import com.m3u.features.stream.fragments.StreamFragmentDefaults.playbackExceptionDisplayText
import com.m3u.i18n.R.string
import com.m3u.material.components.Background
import com.m3u.material.components.Image
import com.m3u.material.components.mask.MaskButton
import com.m3u.material.components.mask.MaskCircleButton
import com.m3u.material.components.mask.MaskState
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.FontFamilies
import com.m3u.ui.Player
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.rememberPlayerState
import kotlinx.coroutines.delay
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Composable
internal fun StreamFragment(
    playerState: StreamState.PlayerState,
    title: String,
    playlistTitle: String,
    cover: String,
    formatsIsNotEmpty: Boolean,
    maskState: MaskState,
    recording: Boolean,
    favourite: Boolean,
    volume: Float,
    brightness: Float,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onRecord: () -> Unit,
    onFavourite: () -> Unit,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    onBackPressed: () -> Unit,
    replay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.colorScheme
    val pref = LocalPref.current
    val helper = LocalHelper.current
    val tv = isTelevision()

    // because they will be updated frequently,
    // they must be wrapped with rememberUpdatedState when using them.
    val currentVolume by rememberUpdatedState(volume)
    val currentBrightness by rememberUpdatedState(brightness)

    val shouldShowSeekbar by remember(
        playerState.player,
        pref.progress,
        playerState.playState
    ) {
        derivedStateOf {
            when {
                !pref.progress -> false
                playerState.player == null -> false
                !playerState.player.isCommandAvailable(COMMAND_GET_CURRENT_MEDIA_ITEM) -> false
                else -> with(playerState.player) {
                    !isCurrentMediaItemDynamic && isCurrentMediaItemSeekable
                }
            }
        }
    }

    val contentPosition by produceState(
        initialValue = -1L,
        shouldShowSeekbar
    ) {
        while (shouldShowSeekbar) {
            delay(50.milliseconds)
            value = playerState.player?.currentPosition ?: -1L
        }
        value = -1L
    }
    val contentDuration by produceState(
        initialValue = -1L,
        shouldShowSeekbar
    ) {
        while (shouldShowSeekbar) {
            delay(50.milliseconds)
            value = playerState.player?.duration?.absoluteValue ?: -1L
        }
        value = -1L
    }

    var bufferedPosition: Long? by remember { mutableStateOf(null) }
    LaunchedEffect(bufferedPosition) {
        delay(800.milliseconds)
        bufferedPosition?.let {
            playerState.player?.seekTo(it)
        }
    }
    LaunchedEffect(playerState.playState) {
        if (playerState.playState == Player.STATE_READY) {
            bufferedPosition = null
        }
    }

    Background(
        color = Color.Black,
        contentColor = Color.White,
    ) {
        Box(modifier) {
            val state = rememberPlayerState(
                player = playerState.player,
                clipMode = pref.clipMode
            )

            Player(
                state = state,
                modifier = Modifier.fillMaxSize()
            )

            val shouldShowPlaceholder =
                !pref.noPictureMode && cover.isNotEmpty() && playerState.videoSize.isEmpty

            CoverPlaceholder(
                visible = shouldShowPlaceholder,
                cover = cover,
                modifier = Modifier.align(Alignment.Center)
            )

            BoxWithConstraints {
                var gesture: MaskGesture? by remember { mutableStateOf(null) }
                val muted = currentVolume == 0f
                PlayerMask(
                    state = maskState,
                    header = {
                        MaskButton(
                            state = maskState,
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            onClick = onBackPressed,
                            contentDescription = stringResource(string.feat_stream_tooltip_on_back_pressed)
                        )
                        Spacer(modifier = Modifier.weight(1f))

                        MaskButton(
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
                            onClick = {
                                onVolume(if (volume != 0f) 0f else 1f)
                            },
                            contentDescription = if (muted) stringResource(string.feat_stream_tooltip_unmute)
                            else stringResource(string.feat_stream_tooltip_mute),
                            tint = when (gesture) {
                                null -> if (muted) theme.error else Color.Unspecified
                                MaskGesture.VOLUME -> if (muted) theme.error else Color.Unspecified
                                MaskGesture.BRIGHTNESS -> Color.Unspecified
                            }
                        )
                        MaskButton(
                            state = maskState,
                            icon = Icons.Rounded.Star,
                            tint = if (favourite) Color(0xffffcd3c) else Color.Unspecified,
                            onClick = onFavourite,
                            contentDescription = if (favourite) stringResource(string.feat_stream_tooltip_unfavourite)
                            else stringResource(string.feat_stream_tooltip_favourite)
                        )
                        if (formatsIsNotEmpty) {
                            MaskButton(
                                state = maskState,
                                icon = Icons.Rounded.HighQuality,
                                onClick = openChooseFormat,
                                contentDescription = stringResource(string.feat_stream_tooltip_choose_format)
                            )
                        }
                        if (pref.record) {
                            MaskButton(
                                state = maskState,
                                icon = if (recording) Icons.Rounded.RadioButtonChecked
                                else Icons.Rounded.RadioButtonUnchecked,
                                tint = if (recording) theme.error
                                else Color.Unspecified,
                                onClick = onRecord,
                                contentDescription = if (recording) stringResource(string.feat_stream_tooltip_unrecord)
                                else stringResource(string.feat_stream_tooltip_record)
                            )
                        }
                        if (!tv && pref.screencast) {
                            MaskButton(
                                state = maskState,
                                icon = Icons.Rounded.Cast,
                                onClick = openDlnaDevices,
                                contentDescription = stringResource(string.feat_stream_tooltip_cast)
                            )
                        }
                        if (!tv && playerState.videoSize.isNotEmpty) {
                            MaskButton(
                                state = maskState,
                                icon = Icons.Rounded.PictureInPicture,
                                onClick = {
                                    helper.enterPipMode(playerState.videoSize)
                                    maskState.sleep()
                                },
                                contentDescription = stringResource(string.feat_stream_tooltip_enter_pip_mode)
                            )
                        }
                    },
                    body = {
                        AnimatedVisibility(
                            visible = pref.alwaysShowReplay || playerState.playState in arrayOf(
                                Player.STATE_IDLE,
                                Player.STATE_ENDED
                            ) || playerState.playerError != null,
                            enter = fadeIn() + scaleIn(initialScale = 0.85f),
                            exit = fadeOut() + scaleOut(targetScale = 0.85f)
                        ) {
                            MaskCircleButton(
                                state = maskState,
                                icon = Icons.Rounded.Refresh,
                                onClick = replay
                            )
                        }
                    },
                    footer = {
                        val spacing = LocalSpacing.current
                        if (pref.fullInfoPlayer && cover.isNotEmpty()) {
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
                                .animateContentSize()
                                .weight(1f)
                        ) {
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
                            val playStateDisplayText = playStateDisplayText(playerState.playState)
                            val exceptionDisplayText =
                                playbackExceptionDisplayText(playerState.playerError)

                            if (playStateDisplayText.isNotEmpty() || exceptionDisplayText.isNotEmpty() || shouldShowSeekbar) {
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
                                    color = theme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                            if (shouldShowSeekbar) {
                                val fontWeight by animateIntAsState(
                                    targetValue = if (bufferedPosition != null) 800
                                    else 400,
                                    label = "position-text-font-weight"
                                )
                                Text(
                                    text = StreamFragmentDefaults.timeunitDisplayTest(
                                        (bufferedPosition ?: contentPosition).toDuration(
                                            DurationUnit.MILLISECONDS
                                        )
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LocalContentColor.current.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    fontWeight = FontWeight(fontWeight),
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        }
                        if (!tv) {
                            val autoRotating by StreamFragmentDefaults.IsAutoRotatingEnabled
                            LaunchedEffect(autoRotating) {
                                if (autoRotating) {
                                    helper.screenOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            }
                            if (pref.screenRotating && !autoRotating) {
                                MaskButton(
                                    state = maskState,
                                    icon = Icons.Rounded.ScreenRotationAlt,
                                    onClick = {
                                        helper.screenOrientation = when (helper.screenOrientation) {
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                        }
                                    },
                                    contentDescription = stringResource(string.feat_stream_tooltip_screen_rotating)
                                )
                            }
                        }
                    },
                    slider = {
                        when {
                            shouldShowSeekbar -> {
                                val animContentPosition by animateFloatAsState(
                                    targetValue = (bufferedPosition
                                        ?: contentPosition.coerceAtLeast(0L)).toFloat(),
                                    label = "anim-content-position"
                                )
                                val enabled = playerState.playState == Player.STATE_READY
                                Slider(
                                    value = animContentPosition,
                                    valueRange = 0f..contentDuration
                                        .coerceAtLeast(0L)
                                        .toFloat(),
                                    enabled = enabled,
                                    onValueChange = {
                                        bufferedPosition = it.roundToLong()
                                        maskState.wake()
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.thenIf(!tv) {
                        Modifier.detectVerticalMaskGestures(
                            safe = 0.35f,
                            threshold = 0.15f,
                            volume = { deltaPixel ->
                                if (!pref.volumeGesture) return@detectVerticalMaskGestures
                                onVolume(
                                    (currentVolume - (deltaPixel / maxHeight.value)).coerceIn(0f..1f)
                                )
                            },
                            brightness = { deltaPixel ->
                                if (!pref.brightnessGesture) return@detectVerticalMaskGestures
                                onBrightness(
                                    (currentBrightness - deltaPixel / maxHeight.value).coerceIn(
                                        0f..1f
                                    )
                                )
                            },
                            onDragStart = {
                                if (!pref.volumeGesture && !pref.brightnessGesture) return@detectVerticalMaskGestures
                                maskState.lock()
                                gesture = it
                            },
                            onDragEnd = {
                                if (!pref.volumeGesture && !pref.brightnessGesture) return@detectVerticalMaskGestures
                                maskState.unlock(400.milliseconds)
                                gesture = null
                            }
                        )
                    }
                )
            }

            LaunchedEffect(playerState.playerError) {
                if (playerState.playerError != null) {
                    maskState.wake()
                }
            }
        }
    }
}
