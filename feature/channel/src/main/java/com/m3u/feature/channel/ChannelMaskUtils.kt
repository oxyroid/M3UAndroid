package com.m3u.feature.channel

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.m3u.i18n.R.string
import kotlin.math.absoluteValue
import kotlin.time.Duration

internal object ChannelMaskUtils {
    fun Modifier.detectVerticalGesture(
        threshold: Float = 0.15f,
        time: Float = 1f,
        onVerticalDrag: (pixel: Float) -> Unit,
        onDragStart: (() -> Unit)? = null,
        onDragEnd: (() -> Unit)? = null
    ): Modifier {
        var total = 0f
        return this then Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { onDragStart?.invoke() },
                onDragEnd = { onDragEnd?.invoke() },
                onVerticalDrag = { change, dragAmount ->
                    total += dragAmount.absoluteValue / size.height
                    if (total < threshold) return@detectVerticalDragGestures
                    onVerticalDrag(dragAmount * time)
                    change.consume()
                }
            )
        }
    }

    @Composable
    fun playbackExceptionDisplayText(e: PlaybackException?): String = when (e) {
        null -> ""
        else -> "[${e.errorCode}] ${e.errorCodeName}"
    }

    @Composable
    fun playStateDisplayText(@Player.State state: Int): String = when (state) {
        Player.STATE_IDLE -> string.feat_channel_playback_state_idle
        Player.STATE_BUFFERING -> string.feat_channel_playback_state_buffering
        Player.STATE_READY -> null
        Player.STATE_ENDED -> string.feat_channel_playback_state_ended
        else -> null
    }
        ?.let { stringResource(it) }
        .orEmpty()

    @Composable
    fun timeunitDisplayTest(duration: Duration): String =
        duration.toComponents { hours, minutes, seconds, _ ->
            buildString {
                if (hours > 0) append("$hours:")
                append("${minutes.fixClockUnit}:")
                append(seconds.fixClockUnit)
            }
        }

    val IsAutoRotatingEnabled: State<Boolean>
        @Composable get() {
            val context = LocalContext.current
            val contentResolver = context.contentResolver
            val initialValue = Settings.System.getInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION
            ) == 1
            return produceState(initialValue) {
                val uri = Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION)
                val handler = Handler(Looper.getMainLooper())
                val observer = object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        value = Settings.System.getInt(
                            contentResolver,
                            Settings.System.ACCELEROMETER_ROTATION
                        ) == 1
                    }
                }
                contentResolver.registerContentObserver(
                    uri,
                    true,
                    observer
                )
                awaitDispose {
                    contentResolver.unregisterContentObserver(observer)
                }
            }
        }

    private val Int.fixClockUnit: String
        get() = if (this < 10) "0$this"
        else this.toString()
}
