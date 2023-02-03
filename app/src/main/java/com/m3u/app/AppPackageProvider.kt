package com.m3u.app

import com.m3u.core.architecture.PackageProvider
import javax.inject.Inject

class AppPackageProvider @Inject constructor(

) : PackageProvider {
    override fun getApplicationID(): String {
        return BuildConfig.APPLICATION_ID
    }

    override fun getVersionName(): String {
        return BuildConfig.VERSION_NAME
    }

    override fun isDebug(): Boolean {
        return BuildConfig.DEBUG
    }
}