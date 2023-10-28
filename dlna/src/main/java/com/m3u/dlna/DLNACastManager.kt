package com.m3u.dlna

import android.app.Activity
import android.app.Application
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.m3u.dlna.http.LocalServer
import com.m3u.dlna.control.CastControlImpl
import com.m3u.dlna.control.DeviceControl
import com.m3u.dlna.control.EmptyDeviceControl
import com.m3u.dlna.control.OnDeviceControlListener
import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.model.message.header.STAllHeader
import org.fourthline.cling.model.message.header.UDADeviceTypeHeader
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.model.types.DeviceType
import org.fourthline.cling.model.types.ServiceType
import org.fourthline.cling.model.types.UDADeviceType
import org.fourthline.cling.model.types.UDAServiceType

object DLNACastManager : OnDeviceRegistryListener {

    val DEVICE_TYPE_MEDIA_RENDERER = UDADeviceType("MediaRenderer")
    val DEVICE_TYPE_MEDIA_SERVER = UDADeviceType("MediaServer")

    val SERVICE_TYPE_AV_TRANSPORT: ServiceType = UDAServiceType("AVTransport")
    val SERVICE_TYPE_RENDERING_CONTROL: ServiceType = UDAServiceType("RenderingControl")
    val SERVICE_TYPE_CONTENT_DIRECTORY: ServiceType = UDAServiceType("ContentDirectory")
    val SERVICE_CONNECTION_MANAGER: ServiceType = UDAServiceType("ConnectionManager")

    private val logger = Logger.create("CastManager")
    private val deviceRegistryImpl = DeviceRegistryImpl(this)
    private var searchDeviceType: DeviceType? = null
    private var upnpService: AndroidUpnpService? = null
    private var applicationContext: Context? = null

    fun bindCastService(context: Context) {
        applicationContext = context.applicationContext
        if (context is Application || context is Activity) {
            context.bindService(Intent(context, DLNACastService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)
        } else {
            logger.e("bindCastService only support Application or Activity implementation.")
        }
    }

    fun unbindCastService(context: Context) {
        if (context is Application || context is Activity) {
            context.unbindService(serviceConnection)
        } else {
            logger.e("bindCastService only support Application or Activity implementation.")
        }
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            val upnpServiceBinder = iBinder as AndroidUpnpService
            if (upnpService !== upnpServiceBinder) {
                upnpService = upnpServiceBinder
                logger.i(String.format("onServiceConnected: [%s]", componentName.shortClassName))
                val registry = upnpServiceBinder.registry
                // add registry listener
                val collection = registry.listeners
                if (collection == null || !collection.contains(deviceRegistryImpl)) {
                    registry.addListener(deviceRegistryImpl)
                }
                search()
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            logger.w(String.format("[%s] onServiceDisconnected", componentName.shortClassName))
            removeRegistryListener()
        }

        override fun onBindingDied(componentName: ComponentName) {
            logger.w(String.format("[%s] onBindingDied", componentName.shortClassName))
            removeRegistryListener()
        }

        private fun removeRegistryListener() {
            upnpService?.registry?.removeListener(deviceRegistryImpl)
            upnpService = null
        }
    }

    // -----------------------------------------------------------------------------------------
    // ---- register or unregister device listener
    // -----------------------------------------------------------------------------------------
    private val registerDeviceListeners: MutableList<OnDeviceRegistryListener> = ArrayList()

    fun registerDeviceListener(listener: OnDeviceRegistryListener?) {
        if (listener == null) return
        upnpService?.also { service ->
            service.registry.devices?.forEach { device ->
                // if some devices has been found, notify first.
                listener.onDeviceAdded(device)
            }
        }
        if (!registerDeviceListeners.contains(listener)) registerDeviceListeners.add(listener)
    }

    fun unregisterListener(listener: OnDeviceRegistryListener) {
        registerDeviceListeners.remove(listener)
    }

    override fun onDeviceAdded(device: Device<*, *, *>) {
        if (checkDeviceType(device)) {
            registerDeviceListeners.forEach { listener -> listener.onDeviceAdded(device) }
        }
    }

    override fun onDeviceRemoved(device: Device<*, *, *>) {
        // TODO:if this device is casting, disconnect first!
        // disconnectDevice(device)
        if (checkDeviceType(device)) {
            registerDeviceListeners.forEach { listener -> listener.onDeviceRemoved(device) }
        }
    }

    private fun checkDeviceType(device: Device<*, *, *>): Boolean = searchDeviceType == null || searchDeviceType == device.type

    // -----------------------------------------------------------------------------------------
    // ---- LocalServer
    // -----------------------------------------------------------------------------------------
    var localServer: LocalServer? = null
        private set

    fun startLocalHttpServer(port: Int = 8192, jetty: Boolean = true) {
        if (localServer == null) {
            applicationContext?.run {
                localServer = LocalServer(this, port, jetty)
            }
        }
        localServer?.startServer()
    }

    fun stopLocalHttpServer() {
        localServer?.stopServer()
    }

    // -----------------------------------------------------------------------------------------
    // ---- Action
    // -----------------------------------------------------------------------------------------
    fun search(type: DeviceType? = null) {
        upnpService?.get()?.also { service ->
            searchDeviceType = type
            service.registry.devices?.filter { searchDeviceType == null || searchDeviceType != it.type }?.onEach {
                // notify device removed without type check.
                registerDeviceListeners.forEach { listener -> listener.onDeviceRemoved(it) }
                service.registry.removeDevice(it.identity.udn)
            }
            // when search device, clear all founded first.
            // service.registry.removeAllRemoteDevices()

            // search the special type device
            service.controlPoint.search(type?.let { UDADeviceTypeHeader(it) } ?: STAllHeader())
        }
    }

    private val deviceControlMap = mutableMapOf<Device<*, *, *>, DeviceControl?>()
    fun connectDevice(device: Device<*, *, *>, listener: OnDeviceControlListener): DeviceControl {
        val service = upnpService?.get() ?: return EmptyDeviceControl
        var control = deviceControlMap[device]
        if (control == null) {
            val newController = CastControlImpl(service.controlPoint, device, listener)
            deviceControlMap[device] = newController
            control = newController
        }
        return control
    }

    fun disconnectDevice(device: Device<*, *, *>) {
        (deviceControlMap[device] as? CastControlImpl)?.released = true
        deviceControlMap[device] = null
    }
}