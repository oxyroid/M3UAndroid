package com.m3u.core.architecture.configuration

import android.content.Context
import android.content.SharedPreferences
import com.m3u.core.annotation.ClipMode
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_AUTO_REFRESH
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_CLIP_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_CONNECT_TIMEOUT
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_EDIT_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_EXPERIMENTAL_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_FEED_STRATEGY
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_ROW_COUNT
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_SCROLL_MODE
import com.m3u.core.architecture.configuration.Configuration.Companion.DEFAULT_USE_COMMON_UI_MODE
import com.m3u.core.util.context.boolean
import com.m3u.core.util.context.int
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class SharedConfiguration @Inject constructor(
    @ApplicationContext context: Context
) : Configuration {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_SETTINGS, Context.MODE_PRIVATE)

    @FeedStrategy
    override var feedStrategy: Int by sharedPreferences.int(DEFAULT_FEED_STRATEGY)
    override var useCommonUIMode: Boolean by sharedPreferences.boolean(DEFAULT_USE_COMMON_UI_MODE)

    override var rowCount: Int by sharedPreferences.int(DEFAULT_ROW_COUNT)

    @ConnectTimeout
    override var connectTimeout: Int by sharedPreferences.int(DEFAULT_CONNECT_TIMEOUT)
    override var editMode: Boolean by sharedPreferences.boolean(DEFAULT_EDIT_MODE)
    override var experimentalMode: Boolean by sharedPreferences.boolean(DEFAULT_EXPERIMENTAL_MODE)

    @ClipMode
    override var clipMode: Int by sharedPreferences.int(DEFAULT_CLIP_MODE)
    override var scrollMode: Boolean by sharedPreferences.boolean(DEFAULT_SCROLL_MODE)
    override var autoRefresh: Boolean by sharedPreferences.boolean(DEFAULT_AUTO_REFRESH)

    companion object {
        private const val SHARED_SETTINGS = "shared_settings"
    }
}
