package com.m3u.dlna

import android.content.Intent
import com.m3u.dlna.android.AndroidUpnpServiceConfiguration
import com.m3u.dlna.android.AndroidUpnpServiceImpl
import org.jupnp.UpnpServiceConfiguration
import org.jupnp.model.types.ServiceType

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