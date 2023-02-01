package com.m3u.data

import android.content.Context
import android.content.SharedPreferences
import com.m3u.core.util.context.int
import com.m3u.core.annotation.SyncMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

private const val SHARED_SETTINGS = "shared_settings"

interface Configuration {
    @SyncMode
    var syncMode: Int
}

class SharedConfiguration @Inject constructor(
    @ApplicationContext context: Context
) : Configuration {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(SHARED_SETTINGS, Context.MODE_PRIVATE)

    @SyncMode
    override var syncMode: Int by sharedPreferences.int(SyncMode.DEFAULT)

}