package com.m3u.data

import android.content.Context
import android.content.SharedPreferences
import com.m3u.core.annotation.ConnectTimeout
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Configuration
import com.m3u.core.architecture.Configuration.Companion.DEFAULT_CONNECT_TIMEOUT
import com.m3u.core.architecture.Configuration.Companion.DEFAULT_EDIT_MODE
import com.m3u.core.architecture.Configuration.Companion.DEFAULT_FEED_STRATEGY
import com.m3u.core.architecture.Configuration.Companion.DEFAULT_MUTED_URLS
import com.m3u.core.architecture.Configuration.Companion.DEFAULT_ROW_COUNT
import com.m3u.core.architecture.Configuration.Companion.DEFAULT_USE_COMMON_UI_MODE
import com.m3u.core.util.context.boolean
import com.m3u.core.util.context.int
import com.m3u.core.util.context.string
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class SharedConfiguration @Inject constructor(
    @ApplicationContext context: Context
) : Configuration {
    private val json = Json
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_SETTINGS, Context.MODE_PRIVATE)

    @FeedStrategy
    override var feedStrategy: Int by sharedPreferences.int(DEFAULT_FEED_STRATEGY)
    override var useCommonUIMode: Boolean by sharedPreferences.boolean(DEFAULT_USE_COMMON_UI_MODE)
    private var mutedUrlsEncoded: String? by sharedPreferences.string(DEFAULT_MUTED_URLS)
    override var mutedUrls: List<String> = emptyList()
        get() = json.decodeFromString(mutedUrlsEncoded ?: DEFAULT_MUTED_URLS) ?: emptyList()
        set(value) {
            mutedUrlsEncoded = json.encodeToString(value)
            field = value
        }
    override var rowCount: Int by sharedPreferences.int(DEFAULT_ROW_COUNT)

    @ConnectTimeout
    override var connectTimeout: Int by sharedPreferences.int(DEFAULT_CONNECT_TIMEOUT)

    override var editMode: Boolean by sharedPreferences.boolean(DEFAULT_EDIT_MODE)

    companion object {
        private const val SHARED_SETTINGS = "shared_settings"
    }
}
