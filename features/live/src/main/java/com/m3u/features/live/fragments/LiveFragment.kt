package com.m3u.features.live.fragments

import android.graphics.Rect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.m3u.core.annotation.ClipMode
import com.m3u.core.util.basic.isNotEmpty
import com.m3u.features.live.R
import com.m3u.features.live.components.LiveMask
import com.m3u.ui.components.Background
import com.m3u.ui.components.ExoPlayer
import com.m3u.ui.components.Image
import com.m3u.ui.components.MaskButton
import com.m3u.ui.components.MaskCircleButton
import com.m3u.ui.components.MaskState
import com.m3u.ui.components.rememberPlayerState
import com.m3u.ui.model.LocalHelper
import com.m3u.ui.model.LocalSpacing
import com.m3u.ui.model.LocalTheme

@Composable
internal fun LiveFragment(
    player: Player?,
    playback: @Player.State Int,
    videoSize: Rect,
    playerError: PlaybackException?,
    title: String,
    feedTitle: String,
    url: String,
    cover: String,
    maskState: MaskState,
    experimentalMode: Boolean,
    @ClipMode clipMode: Int,
    fullInfoPlayer: Boolean,
    recording: Boolean,
    stared: Boolean,
    onRecord: () -> Unit,
    onFavourite: () -> Unit,
    searchDlnaDevices: () -> Unit,
    onBackPressed: () -> Unit,
    onInstallMedia: (String) -> Unit,
    onUninstallMedia: () -> Unit,
    muted: Boolean,
    onMuted: () -> Unit,
    modifier: Modifier = Modifier
) {
    Background(
        color = Color.Black,
        contentColor = Color.White
    ) {
        Box(modifier) {
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

            val shouldShowPlaceholder = cover.isNotEmpty() && videoSize.isEmpty
            if (shouldShowPlaceholder) {
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
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        onClick = onBackPressed,
                        contentDescription = stringResource(R.string.tooltip_on_back_pressed)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    MaskButton(
                        state = maskState,
                        icon = if (muted) Icons.AutoMirrored.Rounded.VolumeMute
                        else Icons.AutoMirrored.Rounded.VolumeUp,
                        onClick = onMuted,
                        contentDescription = if (muted) stringResource(R.string.tooltip_unmute)
                        else stringResource(R.string.tooltip_mute)
                    )
                    MaskButton(
                        state = maskState,
                        icon = Icons.Rounded.Star,
                        tint = if (stared) Color.Yellow else Color.Unspecified,
                        onClick = onFavourite,
                        contentDescription = if (stared) stringResource(R.string.tooltip_unfavourite)
                        else stringResource(R.string.tooltip_favourite)
                    )
                    if (experimentalMode) {
                        MaskButton(
                            state = maskState,
                            icon = if (recording) Icons.Rounded.RadioButtonChecked
                            else Icons.Rounded.RadioButtonUnchecked,
                            tint = if (recording) LocalTheme.current.error
                            else Color.Unspecified,
                            onClick = onRecord,
                            contentDescription = if (recording) stringResource(R.string.tooltip_unrecord)
                            else stringResource(R.string.tooltip_record)
                        )
                        if (playback != Player.STATE_IDLE) {
                            MaskButton(
                                state = maskState,
                                icon = Icons.Rounded.Cast,
                                onClick = searchDlnaDevices,
                                contentDescription = stringResource(R.string.tooltip_cast)
                            )
                        }
                    }
                    if (videoSize.isNotEmpty) {
                        MaskButton(
                            state = maskState,
                            icon = Icons.Rounded.PictureInPicture,
                            onClick = {
                                helper.enterPipMode(videoSize)
                                maskState.sleep()
                            },
                            contentDescription = stringResource(R.string.tooltip_enter_pip_mode)
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
                footer = {
                    val spacing = LocalSpacing.current
                    if (fullInfoPlayer && cover.isNotEmpty()) {
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
                            Spacer(
                                modifier = Modifier.height(spacing.small)
                            )
                        }
                        AnimatedVisibility(playbackDisplayText.isNotEmpty()) {
                            Text(
                                text = playbackDisplayText.uppercase(),
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
                        // TODO: implement servers ui here.

                    }
                }
            )
            LaunchedEffect(playerError) {
                if (playerError != null) {
                    maskState.active()
                }
            }
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
