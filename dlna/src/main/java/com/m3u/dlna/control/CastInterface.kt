package com.m3u.dlna.control

import org.jupnp.model.meta.Device
import org.jupnp.support.avtransport.lastchange.AVTransportVariable
import org.jupnp.support.lastchange.EventedValue
import org.jupnp.support.model.BrowseFlag
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.MediaInfo
import org.jupnp.support.model.PositionInfo
import org.jupnp.support.model.TransportInfo
import org.jupnp.support.model.TransportState
import org.jupnp.support.renderingcontrol.lastchange.EventedValueChannelMute
import org.jupnp.support.renderingcontrol.lastchange.EventedValueChannelVolume

interface DeviceControl : AvTransportServiceAction, RendererServiceAction, ContentServiceAction

object EmptyDeviceControl : DeviceControl {
    override fun setAVTransportURI(
        uri: String,
        title: String,
        callback: ServiceActionCallback<Unit>?
    ) {
    }

    override fun setNextAVTransportURI(
        uri: String,
        title: String,
        callback: ServiceActionCallback<Unit>?
    ) {
    }

    override fun play(speed: String, callback: ServiceActionCallback<Unit>?) {}
    override fun pause(callback: ServiceActionCallback<Unit>?) {}
    override fun stop(callback: ServiceActionCallback<Unit>?) {}
    override fun seek(millSeconds: Long, callback: ServiceActionCallback<Unit>?) {}
    override fun next(callback: ServiceActionCallback<Unit>?) {}
    override fun previous(callback: ServiceActionCallback<Unit>?) {}
    override fun getPositionInfo(callback: ServiceActionCallback<PositionInfo>?) {}
    override fun getMediaInfo(callback: ServiceActionCallback<MediaInfo>?) {}
    override fun getTransportInfo(callback: ServiceActionCallback<TransportInfo>?) {}
    override fun setVolume(volume: Int, callback: ServiceActionCallback<Unit>?) {}
    override fun getVolume(callback: ServiceActionCallback<Int>?) {}
    override fun setMute(mute: Boolean, callback: ServiceActionCallback<Unit>?) {}
    override fun getMute(callback: ServiceActionCallback<Boolean>?) {}
    override fun browse(
        objectId: String,
        flag: BrowseFlag,
        filter: String,
        firstResult: Int,
        maxResults: Int,
        callback: ServiceActionCallback<DIDLContent>?
    ) {
    }

    override fun search(
        containerId: String,
        searchCriteria: String,
        filter: String,
        firstResult: Int,
        maxResults: Int,
        callback: ServiceActionCallback<DIDLContent>?
    ) {
    }
}

interface OnDeviceControlListener {
    fun onConnected(device: Device<*, *, *>) {}
    fun onDisconnected(device: Device<*, *, *>) {}
    fun onEventChanged(event: EventedValue<*>) {
        when (event) {
            is AVTransportVariable.TransportState -> onAvTransportStateChanged(event.value)
            is EventedValueChannelVolume -> onRendererVolumeChanged(event.value.volume)
            is EventedValueChannelMute -> onRendererVolumeMuteChanged(event.value.mute)
        }
    }

    fun onAvTransportStateChanged(state: TransportState) {}
    fun onRendererVolumeChanged(volume: Int) {}
    fun onRendererVolumeMuteChanged(mute: Boolean) {}
}

internal interface SubscriptionListener {
    fun failed(subscriptionId: String?) {}
    fun established(subscriptionId: String?) {}
    fun ended(subscriptionId: String?) {}
    fun onReceived(subscriptionId: String?, event: EventedValue<*>) {}
}
