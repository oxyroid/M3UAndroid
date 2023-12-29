package com.m3u.core.architecture.pref

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import com.m3u.core.architecture.pref.annotation.ClipMode
import com.m3u.core.architecture.pref.annotation.ConnectTimeout
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.architecture.pref.annotation.UnseensMilliseconds
import com.m3u.core.architecture.pref.impl.SnapshotPref
import kotlinx.coroutines.flow.Flow

@Stable
interface Pref {
    @PlaylistStrategy
    var playlistStrategy: Int
    var useCommonUIMode: Boolean
    var rowCount: Int

    @ConnectTimeout
    var connectTimeout: Long
    var godMode: Boolean
    var experimentalMode: Boolean

    @ClipMode
    var clipMode: Int
    var autoRefresh: Boolean
    var fullInfoPlayer: Boolean

    var isSSLVerification: Boolean
    var rootDestination: Int
    var noPictureMode: Boolean

    var cinemaMode: Boolean
    var useDynamicColors: Boolean

    var zappingMode: Boolean
    var brightnessGesture: Boolean
    var volumeGesture: Boolean
    var record: Boolean
    var screencast: Boolean
    var screenRotating: Boolean

    @UnseensMilliseconds
    var unseensMilliseconds: Long

    var autoReconnect: Boolean

    companion object {
        @PlaylistStrategy
        const val DEFAULT_PLAYLIST_STRATEGY = PlaylistStrategy.SKIP_FAVORITE
        const val DEFAULT_USE_COMMON_UI_MODE = false
        const val DEFAULT_ROW_COUNT = 1

        @ConnectTimeout
        const val DEFAULT_CONNECT_TIMEOUT = ConnectTimeout.SHORT
        const val DEFAULT_GOD_MODE = false
        const val DEFAULT_EXPERIMENTAL_MODE = false

        @ClipMode
        const val DEFAULT_CLIP_MODE = ClipMode.ADAPTIVE
        const val DEFAULT_AUTO_REFRESH = false
        const val DEFAULT_SSL_VERIFICATION = false
        const val DEFAULT_FULL_INFO_PLAYER = false
        const val DEFAULT_ROOT_DESTINATION = 0
        const val DEFAULT_NO_PICTURE_MODE = true
        const val DEFAULT_CINEMA_MODE = false

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
        val DEFAULT_USE_DYNAMIC_COLORS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        const val DEFAULT_ZAPPING_MODE = false
        const val DEFAULT_BRIGHTNESS_GESTURE = true
        const val DEFAULT_VOLUME_GESTURE = true
        const val DEFAULT_RECORD = false
        const val DEFAULT_SCREENCAST = true
        const val DEFAULT_SCREEN_ROTATING = false

        @UnseensMilliseconds
        const val DEFAULT_UNSEENS_MILLISECONDS = UnseensMilliseconds.DAYS_3
        const val DEFAULT_AUTO_RECONNECT = false

        const val PLAYLIST_STRATEGY = "playlist-strategy"
        const val USE_COMMON_UI_MODE = "use-common-ui-mode"
        const val ROW_COUNT = "rowCount"

        const val CONNECT_TIMEOUT = "connect-timeout"
        const val GOD_MODE = "god-mode"
        const val EXPERIMENTAL_MODE = "experimental-mode"

        const val CLIP_MODE = "clip-mode"
        const val AUTO_REFRESH = "auto-refresh"
        const val SSL_VERIFICATION = "ssl-verification"
        const val FULL_INFO_PLAYER = "full-info-player"
        const val ROOT_DESTINATION = "root-destination"
        const val NO_PICTURE_MODE = "no-picture-mode"
        const val CINEMA_MODE = "cinema-mode"
        const val USE_DYNAMIC_COLORS = "use-dynamic-colors"
        const val ZAPPING_MODE = "zapping-mode"
        const val BRIGHTNESS_GESTURE = "brightness-gesture"
        const val VOLUME_GESTURE = "volume-gesture"
        const val RECORD = "record"
        const val SCREENCAST = "screencast"
        const val SCREEN_ROTATING = "screen-rotating"
        const val UNSEENS_MILLISECONDS = "unseens-milliseconds"
        const val AUTO_RECONNECT = "auto-reconnect"
    }
}

fun <R> Pref.observeAsFlow(block: (Pref) -> R): Flow<R> = when (this) {
    is SnapshotPref -> snapshotFlow { block(this) }
    else -> error("Pref.observeAsFlow only be allowed when it is snapshot pref.")
}