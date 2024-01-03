package com.m3u.features.stream.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
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

internal object StreamFragmentDefaults {
    /**
     * @param safe The percent of horizontal area from center that will not trigger the gesture.
     * @param threshold The percent of vertical area that can respond to gestures.
     */
    fun Modifier.detectVerticalMaskGestures(
        safe: Float = 0f,
        threshold: Float = 0f,
        volume: (pixel: Float) -> Unit,
        brightness: (pixel: Float) -> Unit,
        onDragStart: ((MaskGesture) -> Unit)? = null,
        onDragEnd: (() -> Unit)? = null
    ): Modifier {
        var gesture: MaskGesture? = null
        var total = 0f
        return this then Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragStart = { start ->
                    when (start.x / size.width) {
                        in 0f..(1 - safe) / 2 ->
                            gesture = MaskGesture.BRIGHTNESS.also { onDragStart?.invoke(it) }

                        in (1 + safe) / 2..1f ->
                            gesture = MaskGesture.VOLUME.also { onDragStart?.invoke(it) }

                        else -> {}
                    }
                    Log.e("TAG", "width: ${size.width}, ${start.x}, r: $gesture")
                },
                onDragEnd = {
                    onDragEnd?.invoke()
                    gesture = null
                    total = 0f
                },
                onDragCancel = {
                    gesture = null
                    total = 0f
                },
                onVerticalDrag = { _, dragAmount ->
                    total += dragAmount.absoluteValue / size.height
                    if (total < threshold) return@detectVerticalDragGestures
                    when (gesture) {
                        MaskGesture.BRIGHTNESS -> brightness(dragAmount)
                        MaskGesture.VOLUME -> volume(dragAmount)
                        null -> {}
                    }
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
        Player.STATE_IDLE -> string.feat_stream_playback_state_idle
        Player.STATE_BUFFERING -> string.feat_stream_playback_state_buffering
        Player.STATE_READY -> null
        Player.STATE_ENDED -> string.feat_stream_playback_state_ended
        else -> null
    }
        ?.let { stringResource(it) }
        .orEmpty()

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
}

class AudioBecomingNoisyReceiver(private val callback: () -> Unit) : BroadcastReceiver() {
    companion object {
        val INTENT_FILTER = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        callback()
    }
}