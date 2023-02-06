package com.m3u.app

import com.m3u.core.architecture.PackageProvider
import javax.inject.Inject

class AppPackageProvider @Inject constructor() : PackageProvider {
    override fun getApplicationID(): String = BuildConfig.APPLICATION_ID
    override fun getVersionName(): String = BuildConfig.VERSION_NAME
    override fun isDebug(): Boolean = BuildConfig.DEBUG
}