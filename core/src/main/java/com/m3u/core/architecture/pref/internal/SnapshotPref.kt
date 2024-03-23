package com.m3u.core.architecture.pref.internal

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.architecture.pref.Pref
import com.m3u.core.architecture.pref.Pref.Companion.ALWAYS_SHOW_REFRESH
import com.m3u.core.architecture.pref.Pref.Companion.ALWAYS_TV
import com.m3u.core.architecture.pref.Pref.Companion.AUTO_REFRESH
import com.m3u.core.architecture.pref.Pref.Companion.BRIGHTNESS_GESTURE
import com.m3u.core.architecture.pref.Pref.Companion.CLIP_MODE
import com.m3u.core.architecture.pref.Pref.Companion.COLOR_ARGB
import com.m3u.core.architecture.pref.Pref.Companion.COMPACT
import com.m3u.core.architecture.pref.Pref.Companion.CONNECT_TIMEOUT
import com.m3u.core.architecture.pref.Pref.Companion.DARK_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_ALWAYS_SHOW_REFRESH
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_ALWAYS_TV
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_AUTO_REFRESH
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_BRIGHTNESS_GESTURE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_CLIP_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_COLOR_ARGB
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_COMPACT
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_CONNECT_TIMEOUT
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_DARK_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_FOLLOW_SYSTEM_THEME
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_FULL_INFO_PLAYER
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_GOD_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_NO_PICTURE_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_PLAYLIST_STRATEGY
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_PROGRESS
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_RECONNECT_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_REMOTE_CONTROL
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_ROOT_DESTINATION
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_ROW_COUNT
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_SCREENCAST
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_SCREEN_ROTATING
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_TUNNELING
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_UNSEENS_MILLISECONDS
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_USE_DYNAMIC_COLORS
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_VOLUME_GESTURE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_ZAPPING_MODE
import com.m3u.core.architecture.pref.Pref.Companion.FOLLOW_SYSTEM_THEME
import com.m3u.core.architecture.pref.Pref.Companion.FULL_INFO_PLAYER
import com.m3u.core.architecture.pref.Pref.Companion.GOD_MODE
import com.m3u.core.architecture.pref.Pref.Companion.NO_PICTURE_MODE
import com.m3u.core.architecture.pref.Pref.Companion.PLAYLIST_STRATEGY
import com.m3u.core.architecture.pref.Pref.Companion.PROGRESS
import com.m3u.core.architecture.pref.Pref.Companion.RECONNECT_MODE
import com.m3u.core.architecture.pref.Pref.Companion.REMOTE_CONTROL
import com.m3u.core.architecture.pref.Pref.Companion.ROOT_DESTINATION
import com.m3u.core.architecture.pref.Pref.Companion.ROW_COUNT
import com.m3u.core.architecture.pref.Pref.Companion.SCREENCAST
import com.m3u.core.architecture.pref.Pref.Companion.SCREEN_ROTATING
import com.m3u.core.architecture.pref.Pref.Companion.TUNNELING
import com.m3u.core.architecture.pref.Pref.Companion.UNSEENS_MILLISECONDS
import com.m3u.core.architecture.pref.Pref.Companion.USE_DYNAMIC_COLORS
import com.m3u.core.architecture.pref.Pref.Companion.VOLUME_GESTURE
import com.m3u.core.architecture.pref.Pref.Companion.ZAPPING_MODE
import com.m3u.core.architecture.pref.annotation.ClipMode
import com.m3u.core.architecture.pref.annotation.ConnectTimeout
import com.m3u.core.architecture.pref.annotation.PlaylistStrategy
import com.m3u.core.architecture.pref.annotation.UnseensMilliseconds
import com.m3u.core.util.context.booleanAsState
import com.m3u.core.util.context.intAsState
import com.m3u.core.util.context.longAsState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@Stable
class SnapshotPref @Inject constructor(
    @ApplicationContext context: Context
) : Pref {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_SETTINGS, Context.MODE_PRIVATE)

    @PlaylistStrategy
    override var playlistStrategy: Int by
    sharedPreferences.intAsState(DEFAULT_PLAYLIST_STRATEGY, PLAYLIST_STRATEGY)

    override var rowCount: Int by
    sharedPreferences.intAsState(DEFAULT_ROW_COUNT, ROW_COUNT)

    @ConnectTimeout
    override var connectTimeout: Long by
    sharedPreferences.longAsState(DEFAULT_CONNECT_TIMEOUT, CONNECT_TIMEOUT)
    override var godMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_GOD_MODE, GOD_MODE)

    @ClipMode
    override var clipMode: Int by
    sharedPreferences.intAsState(DEFAULT_CLIP_MODE, CLIP_MODE)

    override var autoRefresh: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_AUTO_REFRESH, AUTO_REFRESH)

    override var fullInfoPlayer: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_FULL_INFO_PLAYER, FULL_INFO_PLAYER)
    override var rootDestination: Int by
    sharedPreferences.intAsState(DEFAULT_ROOT_DESTINATION, ROOT_DESTINATION)
    override var noPictureMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_NO_PICTURE_MODE, NO_PICTURE_MODE)

    override var darkMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_DARK_MODE, DARK_MODE)
    override var useDynamicColors: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_USE_DYNAMIC_COLORS, USE_DYNAMIC_COLORS)
    override var followSystemTheme: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_FOLLOW_SYSTEM_THEME, FOLLOW_SYSTEM_THEME)

    override var zappingMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_ZAPPING_MODE, ZAPPING_MODE)
    override var brightnessGesture: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_BRIGHTNESS_GESTURE, BRIGHTNESS_GESTURE)
    override var volumeGesture: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_VOLUME_GESTURE, VOLUME_GESTURE)
    override var screencast: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_SCREENCAST, SCREENCAST)
    override var screenRotating: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_SCREEN_ROTATING, SCREEN_ROTATING)

    @UnseensMilliseconds
    override var unseensMilliseconds: Long by
    sharedPreferences.longAsState(DEFAULT_UNSEENS_MILLISECONDS, UNSEENS_MILLISECONDS)
    override var reconnectMode: Int by
    sharedPreferences.intAsState(DEFAULT_RECONNECT_MODE, RECONNECT_MODE)
    override var compact: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_COMPACT, COMPACT)
    override var colorArgb: Int by
    sharedPreferences.intAsState(DEFAULT_COLOR_ARGB, COLOR_ARGB)
    override var tunneling: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_TUNNELING, TUNNELING)
    override var alwaysTv: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_ALWAYS_TV, ALWAYS_TV)
    override var remoteControl: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_REMOTE_CONTROL, REMOTE_CONTROL)
    override var progress: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_PROGRESS, PROGRESS)
    override var alwaysShowReplay: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_ALWAYS_SHOW_REFRESH, ALWAYS_SHOW_REFRESH)

    companion object {
        private const val SHARED_SETTINGS = "shared_settings"
    }

    init {
        alwaysTv = false
    }
}
