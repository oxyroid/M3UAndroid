package com.m3u.androidApp

import android.app.Application
import android.os.Build
import com.m3u.core.architecture.Abi
import com.m3u.core.architecture.Publisher
import com.m3u.core.util.context.tv
import javax.inject.Inject

class AppPublisher @Inject constructor(private val application: Application) : Publisher {
    override val applicationId: String = BuildConfig.APPLICATION_ID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val debug: Boolean = BuildConfig.DEBUG
    override val snapshot: Boolean = BuildConfig.FLAVOR.contains("snapshotChannel", true)
    override val lite: Boolean = BuildConfig.FLAVOR.contains("liteCodec", true)
    override val model: String = Build.MODEL
    override val abi: Abi = Abi.of(Build.SUPPORTED_ABIS[0])
    override val tv: Boolean
        get() = application.resources.configuration.tv
}