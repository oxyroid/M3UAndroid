package com.m3u.dlna

import android.content.Intent
import org.fourthline.cling.UpnpServiceConfiguration
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration
import org.fourthline.cling.android.AndroidUpnpServiceImpl
import org.fourthline.cling.model.types.ServiceType

class DLNACastService : AndroidUpnpServiceImpl() {
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
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