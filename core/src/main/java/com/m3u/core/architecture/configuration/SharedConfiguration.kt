package com.m3u.core.architecture.configuration

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration.Companion.AUTO_REFRESH
import com.m3u.core.architecture.configuration.Configuration.Companion.CINEMA_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.CLIP_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.CONNECT_TIMEOUT
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_AUTO_REFRESH
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_CINEMA_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_CLIP_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_CONNECT_TIMEOUT
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_EXPERIMENTAL_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_FEED_STRATEGY
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_FULL_INFO_PLAYER
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_GOD_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_INITIAL_ROOT_DESTINATION
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_NO_PICTURE_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_ROW_COUNT
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_SCROLL_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_SSL_VERIFICATION
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_USE_COMMON_UI_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_USE_DYNAMIC_COLORS
import com.m3u.core.architecture.configuration.Configuration.Companion.EXPERIMENTAL_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.FEED_STRATEGY
import com.m3u.core.architecture.configuration.Configuration.Companion.FULL_INFO_PLAYER
import com.m3u.core.architecture.configuration.Configuration.Companion.GOD_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.INITIAL_ROOT_DESTINATION
import com.m3u.core.architecture.configuration.Configuration.Companion.NO_PICTURE_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.ROW_COUNT
import com.m3u.core.architecture.configuration.Configuration.Companion.SCROLL_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.SSL_VERIFICATION
import com.m3u.core.architecture.configuration.Configuration.Companion.USE_COMMON_UI_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.USE_DYNAMIC_COLORS
import com.m3u.core.util.context.booleanAsState
import com.m3u.core.util.context.intAsState
import com.m3u.core.util.context.longAsState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * SharedPreferences based Configuration.
 *
 * This implement is an android platform version.
 */
class SharedConfiguration @Inject constructor(
    @ApplicationContext context: Context
) : Configuration {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_SETTINGS, Context.MODE_PRIVATE)

    @FeedStrategy
    override val feedStrategy: MutableState<Int> =
        sharedPreferences.intAsState(DEFAULT_FEED_STRATEGY, FEED_STRATEGY)
    override val useCommonUIMode: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_USE_COMMON_UI_MODE, USE_COMMON_UI_MODE)

    override val rowCount: MutableState<Int> =
        sharedPreferences.intAsState(DEFAULT_ROW_COUNT, ROW_COUNT)

    @ConnectTimeout
    override val connectTimeout: MutableState<Long> =
        sharedPreferences.longAsState(DEFAULT_CONNECT_TIMEOUT, CONNECT_TIMEOUT)
    override val godMode: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_GOD_MODE, GOD_MODE)
    override val experimentalMode: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_EXPERIMENTAL_MODE, EXPERIMENTAL_MODE)

    @ClipMode
    override val clipMode: MutableState<Int> =
        sharedPreferences.intAsState(DEFAULT_CLIP_MODE, CLIP_MODE)
    @ExperimentalConfiguration
    override val scrollMode: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_SCROLL_MODE, SCROLL_MODE)
    override val autoRefresh: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_AUTO_REFRESH, AUTO_REFRESH)
    @ExperimentalConfiguration
    override val isSSLVerification: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_SSL_VERIFICATION, SSL_VERIFICATION)
    override val fullInfoPlayer: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_FULL_INFO_PLAYER, FULL_INFO_PLAYER)
    override val initialRootDestination: MutableState<Int> =
        sharedPreferences.intAsState(DEFAULT_INITIAL_ROOT_DESTINATION, INITIAL_ROOT_DESTINATION)
    override val noPictureMode: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_NO_PICTURE_MODE, NO_PICTURE_MODE)
    @ExperimentalConfiguration
    override val cinemaMode: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_CINEMA_MODE, CINEMA_MODE)
    override val useDynamicColors: MutableState<Boolean> =
        sharedPreferences.booleanAsState(DEFAULT_USE_DYNAMIC_COLORS, USE_DYNAMIC_COLORS)

    companion object {
        private const val SHARED_SETTINGS = "shared_settings"
    }

    init {
        // disabled from 1.13.0-alpha01 (74), because it is not implemented in material-3.
        @OptIn(ExperimentalConfiguration::class)
        cinemaMode.value = false
    }
}
