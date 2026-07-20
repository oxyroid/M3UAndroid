package com.m3u.smartphone.benchmark

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import com.m3u.smartphone.BuildConfig

class DebugBenchmarkSettings private constructor(
    private val contentResolver: ContentResolver
) {
    fun getString(key: String): String? {
        if (!BuildConfig.DEBUG) return null

        return Settings.Global.getString(contentResolver, key)
            ?.takeIf(String::isNotBlank)
    }

    companion object {
        const val DATA_SOURCE = "m3u_benchmark_data_source"
        const val PLAYLIST_TITLE = "m3u_benchmark_playlist_title"
        const val PLAYLIST_URL = "m3u_benchmark_playlist_url"
        const val EPG_TITLE = "m3u_benchmark_epg_title"
        const val EPG_URL = "m3u_benchmark_epg_url"
        const val XTREAM_TITLE = "m3u_benchmark_xtream_title"
        const val XTREAM_BASIC_URL = "m3u_benchmark_xtream_basic_url"
        const val XTREAM_USERNAME = "m3u_benchmark_xtream_username"
        const val XTREAM_PASSWORD = "m3u_benchmark_xtream_password"

        fun from(context: Context): DebugBenchmarkSettings {
            return DebugBenchmarkSettings(context.applicationContext.contentResolver)
        }
    }
}
