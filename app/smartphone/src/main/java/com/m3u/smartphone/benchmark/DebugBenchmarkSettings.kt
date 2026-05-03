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
        const val PLAYLIST_TITLE = "m3u_benchmark_playlist_title"
        const val PLAYLIST_URL = "m3u_benchmark_playlist_url"

        fun from(context: Context): DebugBenchmarkSettings {
            return DebugBenchmarkSettings(context.applicationContext.contentResolver)
        }
    }
}