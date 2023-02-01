package com.m3u

import com.m3u.core.architecture.PackageProvider
import javax.inject.Inject

class AppPackageProvider @Inject constructor() : PackageProvider {
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