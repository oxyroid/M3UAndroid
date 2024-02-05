package com.m3u.androidApp

import com.m3u.core.architecture.Publisher
import javax.inject.Inject

class AppPublisher @Inject constructor() : Publisher {
    override val applicationID: String = BuildConfig.APPLICATION_ID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val debug: Boolean = BuildConfig.DEBUG
    override val snapshot: Boolean =  "snapshot" in BuildConfig.FLAVOR
}