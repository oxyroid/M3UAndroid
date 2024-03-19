package com.m3u.core.architecture.pref

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import com.m3u.core.architecture.pref.annotation.ClipMode
import com.m3u.core.architecture.pref.annotation.ConnectTimeout
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.architecture.pref.annotation.ReconnectMode
import com.m3u.core.architecture.pref.annotation.UnseensMilliseconds
import com.m3u.core.architecture.pref.internal.SnapshotPref
import kotlinx.coroutines.flow.Flow

@Stable
interface Pref {
    @PlaylistStrategy
    var playlistStrategy: Int
    var rowCount: Int

    @ConnectTimeout
    var connectTimeout: Long
    var godMode: Boolean
    var experimentalMode: Boolean

    @ClipMode
    var clipMode: Int
    var autoRefresh: Boolean
    var fullInfoPlayer: Boolean

    var rootDestination: Int
    var noPictureMode: Boolean

    var darkMode: Boolean
    var useDynamicColors: Boolean
    var followSystemTheme: Boolean

    var zappingMode: Boolean
    var brightnessGesture: Boolean
    var volumeGesture: Boolean
    var downloadOrCache: Boolean
    var screencast: Boolean
    var screenRotating: Boolean

    @UnseensMilliseconds
    var unseensMilliseconds: Long

    @ReconnectMode
    var reconnectMode: Int
    var compact: Boolean
    var colorArgb: Int
    var tunneling: Boolean
    var alwaysTv: Boolean
    var remoteControl: Boolean
    var progress: Boolean
    var alwaysShowReplay: Boolean

    companion object {
        @PlaylistStrategy
        const val DEFAULT_PLAYLIST_STRATEGY = PlaylistStrategy.SKIP_FAVORITE
        const val DEFAULT_ROW_COUNT = 1

        @ConnectTimeout
        const val DEFAULT_CONNECT_TIMEOUT = ConnectTimeout.SHORT
        const val DEFAULT_GOD_MODE = false
        const val DEFAULT_EXPERIMENTAL_MODE = false

        @ClipMode
        const val DEFAULT_CLIP_MODE = ClipMode.ADAPTIVE
        const val DEFAULT_AUTO_REFRESH = false
        const val DEFAULT_FULL_INFO_PLAYER = false
        const val DEFAULT_ROOT_DESTINATION = 0
        const val DEFAULT_NO_PICTURE_MODE = true
        const val DEFAULT_DARK_MODE = true

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
        val DEFAULT_USE_DYNAMIC_COLORS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        const val DEFAULT_FOLLOW_SYSTEM_THEME = false

        const val DEFAULT_ZAPPING_MODE = false
        const val DEFAULT_BRIGHTNESS_GESTURE = true
        const val DEFAULT_VOLUME_GESTURE = true
        const val DEFAULT_DOWNLOAD_OR_CACHE = false
        const val DEFAULT_SCREENCAST = true
        const val DEFAULT_SCREEN_ROTATING = false

        @UnseensMilliseconds
        const val DEFAULT_UNSEENS_MILLISECONDS = UnseensMilliseconds.DAYS_3
        const val DEFAULT_RECONNECT_MODE = ReconnectMode.NO
        const val DEFAULT_COMPACT = false
        const val DEFAULT_COLOR_ARGB = 0xD0BCFF
        const val DEFAULT_TUNNELING = false
        const val DEFAULT_ALWAYS_TV = false
        const val DEFAULT_REMOTE_CONTROL = false
        const val DEFAULT_PROGRESS = false
        const val DEFAULT_ALWAYS_SHOW_REFRESH = false

        const val PLAYLIST_STRATEGY = "playlist-strategy"
        const val ROW_COUNT = "rowCount"

        const val CONNECT_TIMEOUT = "connect-timeout"
        const val GOD_MODE = "god-mode"
        const val EXPERIMENTAL_MODE = "experimental-mode"

        const val CLIP_MODE = "clip-mode"
        const val AUTO_REFRESH = "auto-refresh"
        const val FULL_INFO_PLAYER = "full-info-player"
        const val ROOT_DESTINATION = "root-destination"
        const val NO_PICTURE_MODE = "no-picture-mode"
        const val DARK_MODE = "dark-mode"
        const val USE_DYNAMIC_COLORS = "use-dynamic-colors"
        const val FOLLOW_SYSTEM_THEME = "follow-system-theme"
        const val ZAPPING_MODE = "zapping-mode"
        const val BRIGHTNESS_GESTURE = "brightness-gesture"
        const val VOLUME_GESTURE = "volume-gesture"
        const val DOWNLOAD_OR_CACHE = "download-or-cache"
        const val SCREENCAST = "screencast"
        const val SCREEN_ROTATING = "screen-rotating"
        const val UNSEENS_MILLISECONDS = "unseens-milliseconds"
        const val RECONNECT_MODE = "reconnect-mode"
        const val COMPACT = "compact"
        const val COLOR_ARGB = "color-argb"
        const val TUNNELING = "tunneling"
        const val ALWAYS_TV = "always-tv"
        const val REMOTE_CONTROL = "remote-control"
        const val PROGRESS = "progress"
        const val ALWAYS_SHOW_REFRESH = "always-show-refresh"
    }
}

inline fun <R> Pref.observeAsFlow(crossinline block: (Pref) -> R): Flow<R> {
    check(this is SnapshotPref)
    return snapshotFlow { block(this) }
}