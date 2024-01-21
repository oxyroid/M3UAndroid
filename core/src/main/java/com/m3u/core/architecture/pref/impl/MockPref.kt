package com.m3u.core.architecture.pref.impl

import com.m3u.core.architecture.pref.Pref

internal object MockPref : Pref {
    override var playlistStrategy: Int = Pref.DEFAULT_PLAYLIST_STRATEGY
    override var rowCount: Int = Pref.DEFAULT_ROW_COUNT
    override var connectTimeout: Long = Pref.DEFAULT_CONNECT_TIMEOUT
    override var godMode: Boolean = Pref.DEFAULT_GOD_MODE
    override var experimentalMode: Boolean = Pref.DEFAULT_EXPERIMENTAL_MODE
    override var clipMode: Int = Pref.DEFAULT_CLIP_MODE

    override var autoRefresh: Boolean = Pref.DEFAULT_AUTO_REFRESH
    override var fullInfoPlayer: Boolean = Pref.DEFAULT_FULL_INFO_PLAYER

    override var isSSLVerification: Boolean = Pref.DEFAULT_SSL_VERIFICATION
    override var rootDestination: Int = Pref.DEFAULT_ROOT_DESTINATION
    override var noPictureMode: Boolean = Pref.DEFAULT_NO_PICTURE_MODE

    override var darkMode: Boolean = Pref.DEFAULT_DARK_MODE
    override var useDynamicColors: Boolean = Pref.DEFAULT_USE_DYNAMIC_COLORS
    override var zappingMode: Boolean = Pref.DEFAULT_ZAPPING_MODE
    override var brightnessGesture: Boolean = Pref.DEFAULT_BRIGHTNESS_GESTURE
    override var volumeGesture: Boolean = Pref.DEFAULT_VOLUME_GESTURE
    override var record: Boolean = Pref.DEFAULT_RECORD
    override var screencast: Boolean = Pref.DEFAULT_SCREENCAST
    override var screenRotating: Boolean = Pref.DEFAULT_SCREEN_ROTATING
    override var unseensMilliseconds: Long = Pref.DEFAULT_UNSEENS_MILLISECONDS
    override var reconnectMode: Int = Pref.DEFAULT_RECONNECT_MODE
    override var compact: Boolean = Pref.DEFAULT_COMPACT
    override var colorArgb: Int = Pref.DEFAULT_COLOR_ARGB
    override var tunneling: Boolean = Pref.DEFAULT_TUNNELING
}