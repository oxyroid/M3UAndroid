package com.m3u

import com.m3u.core.BuildConfigProvider
import javax.inject.Inject

class AppBuildConfigProvider @Inject constructor(): BuildConfigProvider {
    override fun getName(): String {
        return BuildConfig.APPLICATION_ID
    }

    override fun version(): String {
        return BuildConfig.VERSION_NAME
    }

    override fun isDebug(): Boolean {
        return BuildConfig.DEBUG
    }
}