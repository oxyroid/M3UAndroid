package com.m3u.app

import com.m3u.core.architecture.Publisher
import javax.inject.Inject

class AppPublisher @Inject constructor() : Publisher {
    override val applicationID: String = BuildConfig.APPLICATION_ID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val debug: Boolean = BuildConfig.DEBUG
}