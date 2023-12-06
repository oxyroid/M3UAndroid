package com.m3u.core.architecture.configuration

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import kotlinx.coroutines.flow.Flow

interface Configuration {
    @FeedStrategy
    val feedStrategy: MutableState<Int>
    val useCommonUIMode: MutableState<Boolean>
    val rowCount: MutableState<Int>

    @ConnectTimeout
    val connectTimeout: MutableState<Long>
    val godMode: MutableState<Boolean>
    val experimentalMode: MutableState<Boolean>

    @ClipMode
    val clipMode: MutableState<Int>
    val autoRefresh: MutableState<Boolean>
    val fullInfoPlayer: MutableState<Boolean>

    @ExperimentalConfiguration
    val scrollMode: MutableState<Boolean>

    @ExperimentalConfiguration
    val isSSLVerification: MutableState<Boolean>
    val initialRootDestination: MutableState<Int>
    val noPictureMode: MutableState<Boolean>

    @ExperimentalConfiguration
    val cinemaMode: MutableState<Boolean>
    val useDynamicColors: MutableState<Boolean>

    companion object {
        @FeedStrategy
        const val DEFAULT_FEED_STRATEGY = FeedStrategy.SKIP_FAVORITE
        const val DEFAULT_USE_COMMON_UI_MODE = false
        const val DEFAULT_ROW_COUNT = 1

        @ConnectTimeout
        const val DEFAULT_CONNECT_TIMEOUT = ConnectTimeout.SHORT
        const val DEFAULT_GOD_MODE = false
        const val DEFAULT_EXPERIMENTAL_MODE = false

        @ClipMode
        const val DEFAULT_CLIP_MODE = ClipMode.ADAPTIVE
        const val DEFAULT_SCROLL_MODE = false
        const val DEFAULT_AUTO_REFRESH = false
        const val DEFAULT_SSL_VERIFICATION = true
        const val DEFAULT_FULL_INFO_PLAYER = false
        const val DEFAULT_INITIAL_ROOT_DESTINATION = 0
        const val DEFAULT_NO_PICTURE_MODE = true
        const val DEFAULT_CINEMA_MODE = false

        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
        val DEFAULT_USE_DYNAMIC_COLORS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        const val FEED_STRATEGY = "feedStrategy"
        const val USE_COMMON_UI_MODE = "useCommonUIMode"
        const val ROW_COUNT = "rowCount"

        const val CONNECT_TIMEOUT = "connectTimeout"
        const val GOD_MODE = "godMode"
        const val EXPERIMENTAL_MODE = "experimentalMode"

        const val CLIP_MODE = "clipMode"
        const val SCROLL_MODE = "scrollMode"
        const val AUTO_REFRESH = "autoRefresh"
        const val SSL_VERIFICATION = "sslVerification"
        const val FULL_INFO_PLAYER = "fullInfoPlayer"
        const val INITIAL_ROOT_DESTINATION = "initialRootDestination"
        const val NO_PICTURE_MODE = "noPictureMode"
        const val CINEMA_MODE = "cinemaMode"
        const val USE_DYNAMIC_COLORS = "use-dynamic-colors"
    }
}

fun <T> Configuration.observeAsFlow(selector: (Configuration) -> State<T>): Flow<T> {
    val state by selector(this@Configuration)
    return snapshotFlow { state }
}
