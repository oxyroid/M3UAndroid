package com.m3u.dlna.control

import com.m3u.dlna.DLNACastManager
import com.m3u.dlna.control.BaseServiceExecutor.AVServiceExecutorImpl
import com.m3u.dlna.control.BaseServiceExecutor.ContentServiceExecutorImpl
import com.m3u.dlna.control.BaseServiceExecutor.RendererServiceExecutorImpl
import org.jupnp.controlpoint.ControlPoint
import org.jupnp.model.meta.Device
import org.jupnp.support.avtransport.lastchange.AVTransportLastChangeParser
import org.jupnp.support.lastchange.EventedValue
import org.jupnp.support.model.BrowseFlag
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.MediaInfo
import org.jupnp.support.model.PositionInfo
import org.jupnp.support.model.TransportInfo
import org.jupnp.support.renderingcontrol.lastchange.RenderingControlLastChangeParser

class CastControlImpl(
    controlPoint: ControlPoint,
    device: Device<*, *, *>,
    listener: OnDeviceControlListener,
) : DeviceControl {

    private val avTransportService: AVServiceExecutorImpl
    private val renderService: RendererServiceExecutorImpl
    private val contentService: ContentServiceExecutorImpl
    var released = false

    init {
        avTransportService = AVServiceExecutorImpl(
            controlPoint,
            device.findService(DLNACastManager.SERVICE_TYPE_AV_TRANSPORT)
        )
        avTransportService.subscribe(object : SubscriptionListener {
            override fun failed(subscriptionId: String?) {
                if (!released) listener.onDisconnected(device)
            }

            override fun established(subscriptionId: String?) {
                if (!released) listener.onConnected(device)
            }

            override fun ended(subscriptionId: String?) {
                if (!released) listener.onDisconnected(device)
            }

            override fun onReceived(subscriptionId: String?, event: EventedValue<*>) {
                if (!released) listener.onEventChanged(event)
            }
        }, AVTransportLastChangeParser())
        renderService = RendererServiceExecutorImpl(
            controlPoint,
            device.findService(DLNACastManager.SERVICE_TYPE_RENDERING_CONTROL)
        )
        renderService.subscribe(
            object : SubscriptionListener {},
            RenderingControlLastChangeParser()
        )
        contentService = ContentServiceExecutorImpl(
            controlPoint,
            device.findService(DLNACastManager.SERVICE_TYPE_CONTENT_DIRECTORY)
        )
        contentService.subscribe(object : SubscriptionListener {}, AVTransportLastChangeParser())
    }

    override fun setAVTransportURI(
        uri: String,
        title: String,
        callback: ServiceActionCallback<Unit>?
    ) {
        avTransportService.setAVTransportURI(uri, title, callback)
    }

    override fun setNextAVTransportURI(
        uri: String,
        title: String,
        callback: ServiceActionCallback<Unit>?
    ) {
        avTransportService.setNextAVTransportURI(uri, title, callback)
    }

    override fun play(speed: String, callback: ServiceActionCallback<Unit>?) {
        avTransportService.play(speed, callback)
    }

    override fun pause(callback: ServiceActionCallback<Unit>?) {
        avTransportService.pause(callback)
    }

    override fun seek(millSeconds: Long, callback: ServiceActionCallback<Unit>?) {
        avTransportService.seek(millSeconds, callback)
    }

    override fun stop(callback: ServiceActionCallback<Unit>?) {
        avTransportService.stop(callback)
    }

    override fun next(callback: ServiceActionCallback<Unit>?) {
        avTransportService.next(callback)
    }

    override fun canNext(callback: ServiceActionCallback<Boolean>?) {
        avTransportService.canNext(callback)
    }

    override fun previous(callback: ServiceActionCallback<Unit>?) {
        avTransportService.previous(callback)
    }

    override fun canPrevious(callback: ServiceActionCallback<Boolean>?) {
        avTransportService.canPrevious(callback)
    }

    override fun getMediaInfo(callback: ServiceActionCallback<MediaInfo>?) {
        avTransportService.getMediaInfo(callback)
    }

    override fun getPositionInfo(callback: ServiceActionCallback<PositionInfo>?) {
        avTransportService.getPositionInfo(callback)
    }

    override fun getTransportInfo(callback: ServiceActionCallback<TransportInfo>?) {
        avTransportService.getTransportInfo(callback)
    }

    override fun setVolume(volume: Int, callback: ServiceActionCallback<Unit>?) {
        renderService.setVolume(volume, callback)
    }

    override fun getVolume(callback: ServiceActionCallback<Int>?) {
        renderService.getVolume(callback)
    }

    override fun setMute(mute: Boolean, callback: ServiceActionCallback<Unit>?) {
        renderService.setMute(mute, callback)
    }

    override fun getMute(callback: ServiceActionCallback<Boolean>?) {
        renderService.getMute(callback)
    }

    override fun browse(
        objectId: String,
        flag: BrowseFlag,
        filter: String,
        firstResult: Int,
        maxResults: Int,
        callback: ServiceActionCallback<DIDLContent>?
    ) {
        contentService.browse(objectId, flag, filter, firstResult, maxResults, callback)
    }

    override fun search(
        containerId: String,
        searchCriteria: String,
        filter: String,
        firstResult: Int,
        maxResults: Int,
        callback: ServiceActionCallback<DIDLContent>?
    ) {
        contentService.search(
            containerId,
            searchCriteria,
            filter,
            firstResult,
            maxResults,
            callback
        )
    }
}