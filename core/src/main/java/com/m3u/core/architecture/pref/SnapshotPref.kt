package com.m3u.core.architecture.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.PlaylistStrategy
import com.m3u.core.architecture.pref.Pref.Companion.AUTO_REFRESH
import com.m3u.core.architecture.pref.Pref.Companion.BRIGHTNESS_GESTURE
import com.m3u.core.architecture.pref.Pref.Companion.CINEMA_MODE
import com.m3u.core.architecture.pref.Pref.Companion.CLIP_MODE
import com.m3u.core.architecture.pref.Pref.Companion.CONNECT_TIMEOUT
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_AUTO_REFRESH
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_BRIGHTNESS_GESTURE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_CINEMA_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_CLIP_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_CONNECT_TIMEOUT
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_EXPERIMENTAL_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_PLAYLIST_STRATEGY
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_FULL_INFO_PLAYER
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_GOD_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_NO_PICTURE_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_RECORD
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_ROOT_DESTINATION
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_ROW_COUNT
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_SCREENCAST
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_SSL_VERIFICATION
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_USE_COMMON_UI_MODE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_USE_DYNAMIC_COLORS
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_VOLUME_GESTURE
import com.m3u.core.architecture.pref.Pref.Companion.DEFAULT_ZAPPING_MODE
import com.m3u.core.architecture.pref.Pref.Companion.EXPERIMENTAL_MODE
import com.m3u.core.architecture.pref.Pref.Companion.PLAYLIST_STRATEGY
import com.m3u.core.architecture.pref.Pref.Companion.FULL_INFO_PLAYER
import com.m3u.core.architecture.pref.Pref.Companion.GOD_MODE
import com.m3u.core.architecture.pref.Pref.Companion.NO_PICTURE_MODE
import com.m3u.core.architecture.pref.Pref.Companion.RECORD
import com.m3u.core.architecture.pref.Pref.Companion.ROOT_DESTINATION
import com.m3u.core.architecture.pref.Pref.Companion.ROW_COUNT
import com.m3u.core.architecture.pref.Pref.Companion.SCREENCAST
import com.m3u.core.architecture.pref.Pref.Companion.SSL_VERIFICATION
import com.m3u.core.architecture.pref.Pref.Companion.USE_COMMON_UI_MODE
import com.m3u.core.architecture.pref.Pref.Companion.USE_DYNAMIC_COLORS
import com.m3u.core.architecture.pref.Pref.Companion.VOLUME_GESTURE
import com.m3u.core.architecture.pref.Pref.Companion.ZAPPING_MODE
import com.m3u.core.util.context.booleanAsState
import com.m3u.core.util.context.intAsState
import com.m3u.core.util.context.longAsState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SnapshotPref @Inject constructor(
    @ApplicationContext context: Context
) : Pref {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_SETTINGS, Context.MODE_PRIVATE)

    @PlaylistStrategy
    override var playlistStrategy: Int by
    sharedPreferences.intAsState(DEFAULT_PLAYLIST_STRATEGY, PLAYLIST_STRATEGY)
    override var useCommonUIMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_USE_COMMON_UI_MODE, USE_COMMON_UI_MODE)

    override var rowCount: Int by
    sharedPreferences.intAsState(DEFAULT_ROW_COUNT, ROW_COUNT)

    @ConnectTimeout
    override var connectTimeout: Long by
    sharedPreferences.longAsState(DEFAULT_CONNECT_TIMEOUT, CONNECT_TIMEOUT)
    override var godMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_GOD_MODE, GOD_MODE)
    override var experimentalMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_EXPERIMENTAL_MODE, EXPERIMENTAL_MODE)

    @ClipMode
    override var clipMode: Int by
    sharedPreferences.intAsState(DEFAULT_CLIP_MODE, CLIP_MODE)

    override var autoRefresh: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_AUTO_REFRESH, AUTO_REFRESH)

    @ExperimentalPref
    override var isSSLVerification: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_SSL_VERIFICATION, SSL_VERIFICATION)
    override var fullInfoPlayer: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_FULL_INFO_PLAYER, FULL_INFO_PLAYER)
    override var rootDestination: Int by
    sharedPreferences.intAsState(DEFAULT_ROOT_DESTINATION, ROOT_DESTINATION)
    override var noPictureMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_NO_PICTURE_MODE, NO_PICTURE_MODE)

    @ExperimentalPref
    override var cinemaMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_CINEMA_MODE, CINEMA_MODE)
    override var useDynamicColors: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_USE_DYNAMIC_COLORS, USE_DYNAMIC_COLORS)

    override var zappingMode: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_ZAPPING_MODE, ZAPPING_MODE)

    override var brightnessGesture: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_BRIGHTNESS_GESTURE, BRIGHTNESS_GESTURE)

    override var volumeGesture: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_VOLUME_GESTURE, VOLUME_GESTURE)

    override var record: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_RECORD, RECORD)

    override var screencast: Boolean by
    sharedPreferences.booleanAsState(DEFAULT_SCREENCAST, SCREENCAST)

    companion object {
        private const val SHARED_SETTINGS = "shared_settings"
    }

    init {
        // disabled from 1.13.0-alpha01 (74), because it is not implemented in material-3.
        @OptIn(ExperimentalPref::class)
        cinemaMode = false
    }
}
