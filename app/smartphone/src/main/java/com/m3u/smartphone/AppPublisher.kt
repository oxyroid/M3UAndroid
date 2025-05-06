package com.m3u.smartphone

import android.app.Application
import android.os.Build
import com.m3u.core.architecture.Abi
import com.m3u.core.architecture.Publisher
import javax.inject.Inject

class AppPublisher @Inject constructor(private val application: Application) : Publisher {
    override val applicationId: String = BuildConfig.APPLICATION_ID
    override val versionName: String = BuildConfig.VERSION_NAME
    override val versionCode: Int = BuildConfig.VERSION_CODE
    override val debug: Boolean = BuildConfig.DEBUG
    override val model: String = Build.MODEL
    override val abi: Abi = Abi.of(Build.SUPPORTED_ABIS[0])
}