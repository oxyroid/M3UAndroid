package com.m3u.core.architecture.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.architecture.preferences.annotation.ClipMode
import com.m3u.core.architecture.preferences.annotation.ConnectTimeout
import com.m3u.core.architecture.preferences.annotation.PlaylistStrategy
import com.m3u.core.architecture.preferences.annotation.ReconnectMode
import com.m3u.core.architecture.preferences.annotation.UnseensMilliseconds
import com.m3u.core.util.context.booleanAsState
import com.m3u.core.util.context.intAsState
import com.m3u.core.util.context.longAsState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Stable
@Singleton
class Preferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_SETTINGS, Context.MODE_PRIVATE)

    @PlaylistStrategy
    var playlistStrategy: Int by
    sharedPreferences.intAsState(DEFAULT_PLAYLIST_STRATEGY, PLAYLIST_STRATEGY)

    var rowCount: Int by
    sharedPreferences.intAsState(DEFAULT_ROW_COUNT, ROW_COUNT)

    @ConnectTimeout
    var connectTimeout: Long by
    sharedPreferences.longAsState(DEFAULT_CONNECT_TIMEOUT, CONNECT_TIMEOUT)
    var godMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_GOD_MODE, GOD_MODE)

    @ClipMode
    var clipMode: Int by
    sharedPreferences.intAsState(DEFAULT_CLIP_MODE, CLIP_MODE)

    var autoRefresh: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_AUTO_REFRESH, AUTO_REFRESH)

    var fullInfoPlayer: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_FULL_INFO_PLAYER, FULL_INFO_PLAYER)
    var rootDestination: Int by
    sharedPreferences.intAsState(DEFAULT_ROOT_DESTINATION, ROOT_DESTINATION)
    var noPictureMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_NO_PICTURE_MODE, NO_PICTURE_MODE)

    var darkMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_DARK_MODE, DARK_MODE)
    var useDynamicColors: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_USE_DYNAMIC_COLORS, USE_DYNAMIC_COLORS)
    var followSystemTheme: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_FOLLOW_SYSTEM_THEME, FOLLOW_SYSTEM_THEME)

    var zappingMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_ZAPPING_MODE, ZAPPING_MODE)
    var brightnessGesture: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_BRIGHTNESS_GESTURE, BRIGHTNESS_GESTURE)
    var volumeGesture: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_VOLUME_GESTURE, VOLUME_GESTURE)
    var screencast: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_SCREENCAST, SCREENCAST)
    var screenRotating: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_SCREEN_ROTATING, SCREEN_ROTATING)

    @UnseensMilliseconds
    var unseensMilliseconds: Long by
    sharedPreferences.longAsState(DEFAULT_UNSEENS_MILLISECONDS, UNSEENS_MILLISECONDS)
    var reconnectMode: Int by
    sharedPreferences.intAsState(DEFAULT_RECONNECT_MODE, RECONNECT_MODE)
    var argb: Int by
    sharedPreferences.intAsState(DEFAULT_COLOR_ARGB, COLOR_ARGB)
    var tunneling: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_TUNNELING, TUNNELING)
    var alwaysTv: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_ALWAYS_TV, ALWAYS_TV)
    var remoteControl: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_REMOTE_CONTROL, REMOTE_CONTROL)
    var progress: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_PROGRESS, PROGRESS)
    var alwaysShowReplay: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_ALWAYS_SHOW_REFRESH, ALWAYS_SHOW_REFRESH)
    var paging: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_PAGING, PAGING)
    var panel: Boolean by sharedPreferences.booleanAsState(DEFAULT_PLAYER_PANEL, PLAYER_PANEL)
    var cache: Boolean by sharedPreferences.booleanAsState(DEFAULT_CACHE, CACHE)

    companion object {
        private const val SHARED_SETTINGS = "shared_settings"

        @PlaylistStrategy
        const val DEFAULT_PLAYLIST_STRATEGY = PlaylistStrategy.KEEP
        const val DEFAULT_ROW_COUNT = 1

        @ConnectTimeout
        const val DEFAULT_CONNECT_TIMEOUT = ConnectTimeout.SHORT
        const val DEFAULT_GOD_MODE = false

        @ClipMode
        const val DEFAULT_CLIP_MODE = ClipMode.ADAPTIVE
        const val DEFAULT_AUTO_REFRESH = false
        const val DEFAULT_FULL_INFO_PLAYER = false
        const val DEFAULT_ROOT_DESTINATION = 0
        const val DEFAULT_NO_PICTURE_MODE = false
        const val DEFAULT_DARK_MODE = true

        const val DEFAULT_USE_DYNAMIC_COLORS = false
        const val DEFAULT_FOLLOW_SYSTEM_THEME = false

        const val DEFAULT_ZAPPING_MODE = false
        const val DEFAULT_BRIGHTNESS_GESTURE = true
        const val DEFAULT_VOLUME_GESTURE = true
        const val DEFAULT_SCREENCAST = true
        const val DEFAULT_SCREEN_ROTATING = false

        @UnseensMilliseconds
        const val DEFAULT_UNSEENS_MILLISECONDS = UnseensMilliseconds.DAYS_3
        const val DEFAULT_RECONNECT_MODE = ReconnectMode.NO
        const val DEFAULT_COLOR_ARGB = 0x5E6738
        const val DEFAULT_TUNNELING = false
        const val DEFAULT_ALWAYS_TV = false
        const val DEFAULT_REMOTE_CONTROL = false
        const val DEFAULT_PROGRESS = true
        const val DEFAULT_ALWAYS_SHOW_REFRESH = false
        const val DEFAULT_PAGING = false
        const val DEFAULT_PLAYER_PANEL = true
        const val DEFAULT_CACHE = false

        const val PLAYLIST_STRATEGY = "playlist-strategy"
        const val ROW_COUNT = "rowCount"

        const val CONNECT_TIMEOUT = "connect-timeout"
        const val GOD_MODE = "god-mode"

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
        const val SCREENCAST = "screencast"
        const val SCREEN_ROTATING = "screen-rotating"
        const val UNSEENS_MILLISECONDS = "unseens-milliseconds"
        const val RECONNECT_MODE = "reconnect-mode"
        const val COLOR_ARGB = "color-argb"
        const val TUNNELING = "tunneling"
        const val ALWAYS_TV = "always-tv"
        const val REMOTE_CONTROL = "remote-control"
        const val PROGRESS = "progress"
        const val ALWAYS_SHOW_REFRESH = "always-show-refresh"
        const val PAGING = "paging"
        const val PLAYER_PANEL = "player_panel"
        const val CACHE = "cache"
    }

    init {
        alwaysTv = false
    }
}
