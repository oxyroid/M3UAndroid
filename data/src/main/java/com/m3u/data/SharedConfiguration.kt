package com.m3u.data

import android.content.Context
import android.content.SharedPreferences
import com.m3u.core.annotation.FeedStrategy
import com.m3u.core.architecture.Configuration
import com.m3u.core.util.context.boolean
import com.m3u.core.util.context.int
import com.m3u.core.util.context.string
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val SHARED_SETTINGS = "shared_settings"

class SharedConfiguration @Inject constructor(
    @ApplicationContext context: Context
) : Configuration {
    private val json = Json
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_SETTINGS, Context.MODE_PRIVATE)

    @FeedStrategy
    override var feedStrategy: Int by sharedPreferences.int(FeedStrategy.ALL)
    override var useCommonUIMode: Boolean by sharedPreferences.boolean(true)
    private var mutedUrlsEncoded: String? by sharedPreferences.string()
    override var mutedUrls: List<String> = emptyList()
        get() = json.decodeFromString(mutedUrlsEncoded ?: "[]") ?: emptyList()
        set(value) {
            mutedUrlsEncoded = json.encodeToString(value)
            field = value
        }
}