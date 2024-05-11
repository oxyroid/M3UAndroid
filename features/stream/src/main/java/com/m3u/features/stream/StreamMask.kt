package com.m3u.features.stream

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.m3u.core.architecture.preferences.hiltPreferences
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.data.database.model.Programme
import com.m3u.data.television.model.RemoteDirection
import com.m3u.features.stream.components.CustomTextIcon
import com.m3u.features.stream.components.MaskTextButton
import com.m3u.features.stream.components.PlayerMask
import com.m3u.i18n.R.string
import com.m3u.material.components.mask.MaskButton
import com.m3u.material.components.mask.MaskCircleButton
import com.m3u.material.components.mask.MaskPanel
import com.m3u.material.components.mask.MaskState
import com.m3u.material.ktx.isTelevision
import com.m3u.material.ktx.thenIf
import com.m3u.material.model.LocalSpacing
import com.m3u.ui.FontFamilies
import com.m3u.ui.Image
import com.m3u.ui.helper.LocalHelper
import com.m3u.ui.util.TimeUtils.formatEOrSh
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal enum class TelevisionKeyCode(val nativeCode: Int) {
    UP(KeyEvent.KEYCODE_DPAD_UP),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT);
}

//@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun
StreamMask(
    cover: String,
    title: String,
    playlistTitle: String,
    playerState: PlayerState,
    volume: Float,
    brightness: Float,
    gesture: MaskGesture?,
    maskState: MaskState,
    favourite: Boolean,
    isSeriesPlaylist: Boolean,
    isVodPlaylist: Boolean,
    isPanelExpanded: Boolean,
    formatsIsNotEmpty: Boolean,
    onFavourite: () -> Unit,
    onBackPressed: () -> Unit,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    onEnterPipMode: () -> Unit,
    onVolume: (Float) -> Unit,
    onKeyCode: (RemoteDirection) -> Unit,
    modifier: Modifier = Modifier,
    getProgrammeCurrently: suspend () -> Programme?,
) {
    val preferences = hiltPreferences()
    val helper = LocalHelper.current
    val spacing = LocalSpacing.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentGetProgrammeCurrently by rememberUpdatedState(getProgrammeCurrently)
    val programme: Programme? by produceState<Programme?>(null) {
        value = currentGetProgrammeCurrently()
    }

    val isNew = programme?.isNew
    val isNew2 = programme?.isNewTag
    val isLive = programme?.isLive
    val isLive2 = programme?.isLiveTag

    val tv = isTelevision()
    val coroutineScope = rememberCoroutineScope()

    // because they will be updated frequently,
    // they must be wrapped with rememberUpdatedState when using them.
    val currentVolume by rememberUpdatedState(volume)
    val currentBrightness by rememberUpdatedState(brightness)
    val currentGesture by rememberUpdatedState(gesture)
    val muted = currentVolume == 0f

    val defaultBrightnessOrVolumeContentDescription = when {
        muted -> stringResource(string.feat_stream_tooltip_unmute)
        else -> stringResource(string.feat_stream_tooltip_mute)
    }

    val brightnessOrVolumeText by remember {
        derivedStateOf {
            when (currentGesture) {
                MaskGesture.VOLUME -> "${(currentVolume.coerceIn(0f..1f) * 100).roundToInt()}%"
                MaskGesture.BRIGHTNESS -> "${(currentBrightness.coerceIn(0f..1f) * 100).roundToInt()}%"
                else -> null
            }
        }
    }

    val isProgressEnabled = preferences.progress
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

    LaunchedEffect(playerState.playState) {
        if (playerState.playState == Player.STATE_READY) {
            bufferedPosition = null
        }
    }

    if (tv) {
        BackHandler(maskState.visible) {
            maskState.sleep()
        }
    }

    Box {
        MaskPanel(
            state = maskState,
            modifier = Modifier.thenIf(tv) {
                Modifier.onKeyEvent { event ->
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> (!maskState.visible).also {
                            if (it) onKeyCode(RemoteDirection.UP)
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN -> (!maskState.visible).also {
                            if (it) onKeyCode(RemoteDirection.DOWN)
                        }

                        else -> false
                    }
                }
            }
        )

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

                MaskTextButton(
                    state = maskState,
                    icon = when (currentGesture) {
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
                    tint = when (currentGesture) {
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
                        contentDescription = if (favourite) stringResource(string.feat_stream_tooltip_unfavourite)
                        else stringResource(string.feat_stream_tooltip_favourite)
                    )
                }

                if (formatsIsNotEmpty) {
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.HighQuality,
                        onClick = openChooseFormat,
                        contentDescription = stringResource(string.feat_stream_tooltip_choose_format)
                    )
                }

                if (!tv && preferences.screencast) {
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
                        onClick = onEnterPipMode,
                        contentDescription = stringResource(string.feat_stream_tooltip_enter_pip_mode)
                    )
                }
            },
            body = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = (!isPanelExpanded && preferences.alwaysShowReplay) || playerState.playState in arrayOf(
                            Player.STATE_IDLE,
                            Player.STATE_ENDED
                        ) || playerState.playerError != null,
                        enter = fadeIn() + scaleIn(initialScale = 0.85f),
                        exit = fadeOut() + scaleOut(targetScale = 0.85f)
                    ) {
                        MaskCircleButton(
                            state = maskState,
                            icon = Icons.Rounded.Refresh,
                            onClick = {
                                coroutineScope.launch { helper.replay() }
                            }
                        )
                    }
                }
            },
            footer = {
                AnimatedVisibility(
                visible = !isPanelExpanded,
                enter = fadeIn(),
                exit = fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .semantics(mergeDescendants = true) { }
                            .animateContentSize()
                            .height(80.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(2f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(0.5f)
                            ) {
                                if (preferences.fullInfoPlayer && cover.isNotEmpty()) {
                                    Image(
                                        model = cover,
                                        modifier = Modifier
                                            .size(60.dp)
                                            .align(Alignment.CenterHorizontally)
                                            .clip(RoundedCornerShape(spacing.small))
                                    )
                                }
                            }
                            Column(
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.weight(3f)
                                ) {
                                Text(
                                    text = title.trim(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    maxLines = 1,
                                    color = LocalContentColor.current.copy(0.54f),
                                    fontFamily = FontFamilies.LexendExa,
                                    textAlign = TextAlign.Center, // Align text horizontally
                                    modifier = Modifier
                                        .height(60.dp)
                                        .basicMarquee()
                                        .wrapContentHeight(align = Alignment.CenterVertically) // Align text vertically
                                        .padding(start = if (isLandscape) 0.dp else 16.dp) // Add left padding only in landscape mode
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(0.5f)
                                    .padding(4.dp)
                            ) {
                                if (preferences.fullInfoPlayer && (isNew == true || isNew2 == true)) {
                                    CustomTextIcon(
                                        text = "NEW",
                                        textColor = Color.White,
                                        backgroundColor = Color.Blue,
                                        modifier = Modifier
                                    )
                                } else if (preferences.fullInfoPlayer && (isLive == true || isLive2 == true)) {
                                    CustomTextIcon(
                                        text = "LIVE",
                                        textColor = Color.White,
                                        backgroundColor = Color.Red,
                                        modifier = Modifier
                                    )
                                }
                            }
                            Column(
                                modifier = Modifier.weight(3f)
                            ) {
                                Text(
                                    text = if (isSeriesPlaylist || isVodPlaylist) playlistTitle.trim()
                                        .uppercase() else programme?.readText() ?: "",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    color = LocalContentColor.current.copy(0.54f),
                                    fontFamily = FontFamilies.LexendExa,
                                    modifier = Modifier
                                        .height(30.dp)
                                        .basicMarquee()
                                        .wrapContentHeight(align = Alignment.CenterVertically)
                                        .padding(start = if (isLandscape) 0.dp else 16.dp) // Add left padding only in landscape mode
                                )
                            }
                        }
                    }
                }
                    val playStateDisplayText =
                        PlayerMaskDefaults.playStateDisplayText(playerState.playState)
                    val exceptionDisplayText =
                        PlayerMaskDefaults.playbackExceptionDisplayText(playerState.playerError)

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

                if (!tv) {
                    val autoRotating by PlayerMaskDefaults.IsAutoRotatingEnabled
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
                            contentDescription = stringResource(string.feat_stream_tooltip_screen_rotating)
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
                                text = PlayerMaskDefaults.timeunitDisplayTest(
                                    (bufferedPosition ?: contentPosition)
                                        .toDuration(DurationUnit.MILLISECONDS)
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = 0.75f),
                                maxLines = 1,
                                fontWeight = FontWeight(fontWeight),
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee()
                            )
                            Slider(
                                value = animContentPosition,
                                valueRange = 0f..contentDuration
                                    .coerceAtLeast(0L)
                                    .toFloat(),
                                onValueChange = {
                                    bufferedPosition = it.roundToLong()
                                    maskState.wake()
                                },
                                onValueChangeFinished = {
                                    bufferedPosition?.let { playerState.player?.seekTo(it) }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .onKeyEvent { event ->
                                        when (event.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                onKeyCode(RemoteDirection.LEFT)
                                                maskState.wake()
                                                true
                                            }

                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                onKeyCode(RemoteDirection.RIGHT)
                                                maskState.wake()
                                                true
                                            }

                                            else -> false
                                        }
                                    }
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
private fun Programme.readText(): String {
    val preferences = hiltPreferences()
    val clockMode = preferences.twelveHourClock
    val title = title

    return buildString {
        val start = Instant.fromEpochMilliseconds(start)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .formatEOrSh(clockMode)

        if (isNew) {
            val indexOfSubstring = title.indexOf("ᴺᵉʷ")
            val trimmedTitle = title.substring(0, indexOfSubstring).trim()
            append("$start $trimmedTitle")
        } else if(isLive) {
            val indexOfSubstring = title.indexOf("ᴸᶦᵛᵉ")
            val trimmedTitle = title.substring(0, indexOfSubstring).trim()
            append("$start $trimmedTitle")
        } else {
            append("$start $title")
        }
    }
}