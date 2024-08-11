package com.m3u.data.tv.nsd

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

internal class NsdResolveListener(
    private val onResolved: (NsdServiceInfo?) -> Unit,
    private val onResolveFailed: (NsdServiceInfo?) -> Unit,
    private val onResolvedStopped: (NsdServiceInfo) -> Unit = {},
    private val onStopResolutionFailed: (NsdServiceInfo) -> Unit = {}
) : NsdManager.ResolveListener {
    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
        onResolved(serviceInfo)
    }

    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        onResolveFailed(serviceInfo)
    }

    override fun onResolutionStopped(serviceInfo: NsdServiceInfo) {
        super.onResolutionStopped(serviceInfo)
        onResolvedStopped(serviceInfo)
    }

    override fun onStopResolutionFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        super.onStopResolutionFailed(serviceInfo, errorCode)
        onStopResolutionFailed(serviceInfo)
    }
}