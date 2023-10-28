package com.m3u.dlna

import android.content.Intent
import org.fourthline.cling.UpnpServiceConfiguration
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration
import org.fourthline.cling.android.AndroidUpnpServiceImpl
import org.fourthline.cling.model.types.ServiceType

class DLNACastService : AndroidUpnpServiceImpl() {
    private val logger = Logger.create("CastService")
    override fun onCreate() {
        logger.i("DLNACastService onCreate")
//        LoggingUtil.resetRootHandler(FixedAndroidLogHandler())
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        logger.i("DLNACastService onStartCommand: $flags, $startId, $intent")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        logger.w("DLNACastService onDestroy")
        super.onDestroy()
    }

    override fun createConfiguration(): UpnpServiceConfiguration =
        object : AndroidUpnpServiceConfiguration() {
            override fun getExclusiveServiceTypes(): Array<ServiceType> = arrayOf(
                DLNACastManager.SERVICE_TYPE_AV_TRANSPORT,
                DLNACastManager.SERVICE_TYPE_RENDERING_CONTROL,
                DLNACastManager.SERVICE_TYPE_CONTENT_DIRECTORY,
                DLNACastManager.SERVICE_CONNECTION_MANAGER
            )
        }
}