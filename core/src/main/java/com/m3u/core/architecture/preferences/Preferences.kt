package com.m3u.core.architecture.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.m3u.core.util.context.booleanAsState
import com.m3u.core.util.context.intAsState
import com.m3u.core.util.context.longAsState
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Composable
fun hiltPreferences(): Preferences {
    val context = LocalContext.current
    return remember {
        val applicationContext = context.applicationContext ?: throw IllegalStateException()
        EntryPointAccessors
            .fromApplication<PreferencesEntryPoint>(applicationContext)
            .preferences
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface PreferencesEntryPoint {
    val preferences: Preferences
}

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

    var autoRefreshChannels: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_AUTO_REFRESH_CHANNELS, AUTO_REFRESH_CHANNELS)

    var fullInfoPlayer: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_FULL_INFO_PLAYER, FULL_INFO_PLAYER)
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
    var remoteControl: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_REMOTE_CONTROL, REMOTE_CONTROL)
    var twelveHourClock: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_12_H_CLOCK_MODE, CLOCK_MODE)
    var slider: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_SLIDER, SLIDER)
    var alwaysShowReplay: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_ALWAYS_SHOW_REFRESH, ALWAYS_SHOW_REFRESH)
    var paging: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_PAGING, PAGING)
    var panel: Boolean by sharedPreferences.booleanAsState(DEFAULT_PLAYER_PANEL, PLAYER_PANEL)
    var cache: Boolean by sharedPreferences.booleanAsState(DEFAULT_CACHE, CACHE)
    var randomlyInFavourite: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_RANDOMLY_IN_FAVOURITE, RANDOMLY_IN_FAVOURITE)
    var colorfulBackground by
    sharedPreferences.booleanAsState(DEFAULT_COLORFUL_BACKGROUND, COLORFUL_BACKGROUND)
    var compactDimension by
    sharedPreferences.booleanAsState(DEFAULT_COMPACT_DIMENSION, COMPACT_DIMENSION)


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
        const val DEFAULT_AUTO_REFRESH_CHANNELS = false
        const val DEFAULT_FULL_INFO_PLAYER = false
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
        const val DEFAULT_REMOTE_CONTROL = false
        const val DEFAULT_SLIDER = true
        const val DEFAULT_ALWAYS_SHOW_REFRESH = false
        const val DEFAULT_PAGING = true
        const val DEFAULT_PLAYER_PANEL = true
        const val DEFAULT_CACHE = false
        const val DEFAULT_RANDOMLY_IN_FAVOURITE = false

        const val DEFAULT_12_H_CLOCK_MODE = false
        const val DEFAULT_COLORFUL_BACKGROUND = false
        const val DEFAULT_COMPACT_DIMENSION = false

        const val PLAYLIST_STRATEGY = "playlist-strategy"
        const val ROW_COUNT = "rowCount"

        const val CONNECT_TIMEOUT = "connect-timeout"
        const val GOD_MODE = "god-mode"

        const val CLIP_MODE = "clip-mode"
        const val AUTO_REFRESH_CHANNELS = "auto-refresh-channels"
        const val FULL_INFO_PLAYER = "full-info-player"
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
        const val CLOCK_MODE = "12h-clock-mode"
        const val REMOTE_CONTROL = "remote-control"

        const val SLIDER = "slider"
        const val ALWAYS_SHOW_REFRESH = "always-show-refresh"
        const val PAGING = "paging"
        const val PLAYER_PANEL = "player_panel"
        const val CACHE = "cache"
        const val RANDOMLY_IN_FAVOURITE = "randomly-in-favourite"

        const val COLORFUL_BACKGROUND = "colorful-background"
        const val COMPACT_DIMENSION = "compact-dimension"
    }
}
