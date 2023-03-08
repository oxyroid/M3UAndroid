package com.m3u.app

import com.m3u.core.architecture.Packager
import javax.inject.Inject

class AppPackager @Inject constructor() : Packager {
    override val applicationID: String = BuildConfig.APPLICATION_ID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val debug: Boolean = BuildConfig.DEBUG
}