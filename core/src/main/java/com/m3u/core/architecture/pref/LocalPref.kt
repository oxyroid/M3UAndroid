package com.m3u.core.architecture.pref

import androidx.compose.runtime.compositionLocalOf

val LocalPref = compositionLocalOf<Pref> { MockPref }

private object MockPref: Pref {
    override var feedStrategy: Int = Pref.DEFAULT_FEED_STRATEGY
    override var useCommonUIMode: Boolean = Pref.DEFAULT_USE_COMMON_UI_MODE
    override var rowCount: Int = Pref.DEFAULT_ROW_COUNT
    override var connectTimeout: Long = Pref.DEFAULT_CONNECT_TIMEOUT
    override var godMode: Boolean = Pref.DEFAULT_GOD_MODE
    override var experimentalMode: Boolean = Pref.DEFAULT_EXPERIMENTAL_MODE
    override var clipMode: Int = Pref.DEFAULT_CLIP_MODE

    override var autoRefresh: Boolean = Pref.DEFAULT_AUTO_REFRESH
    override var fullInfoPlayer: Boolean = Pref.DEFAULT_FULL_INFO_PLAYER

    @ExperimentalPref
    override var scrollMode: Boolean = Pref.DEFAULT_SCROLL_MODE

    @ExperimentalPref
    override var isSSLVerification: Boolean = Pref.DEFAULT_SSL_VERIFICATION
    override var rootDestination: Int = Pref.DEFAULT_INITIAL_ROOT_DESTINATION
    override var noPictureMode: Boolean = Pref.DEFAULT_NO_PICTURE_MODE

    @ExperimentalPref
    override var cinemaMode: Boolean = Pref.DEFAULT_CINEMA_MODE
    override var useDynamicColors: Boolean = Pref.DEFAULT_USE_DYNAMIC_COLORS
    override var zapMode: Boolean = Pref.DEFAULT_ZAP_MODE
}