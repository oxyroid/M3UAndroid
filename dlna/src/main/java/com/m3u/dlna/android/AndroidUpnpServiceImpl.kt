package com.m3u.dlna.android

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import org.jupnp.UpnpService
import org.jupnp.UpnpServiceConfiguration
import org.jupnp.UpnpServiceImpl
import org.jupnp.controlpoint.ControlPoint
import org.jupnp.protocol.ProtocolFactory
import org.jupnp.registry.Registry
import org.jupnp.transport.Router

open class AndroidUpnpServiceImpl: Service() {
    private val upnp: UpnpService by lazy {
        object : UpnpServiceImpl(createConfiguration()) {
            init {
                startup()
            }
            override fun createRouter(
                protocolFactory: ProtocolFactory,
                registry: Registry
            ): Router {
                return this@AndroidUpnpServiceImpl.createRouter(
                    configuration,
                    protocolFactory,
                    this@AndroidUpnpServiceImpl
                )
            }

            override fun shutdown() {
                (getRouter() as AndroidRouter).unregisterBroadcastReceiver()
                super.shutdown(true)
            }
        }
    }
    private val binder = object : Binder(), AndroidUpnpService {
        override val service: UpnpService
            get() = upnp
        override val configuration: UpnpServiceConfiguration
            get() = upnp.configuration
        override val registry: Registry
            get() = upnp.registry
        override val controlPoint: ControlPoint
            get() = upnp.controlPoint
    }
    override fun onBind(intent: Intent): IBinder = binder

    open fun createConfiguration(): UpnpServiceConfiguration = AndroidUpnpServiceConfiguration()

    protected fun createRouter(
        configuration: UpnpServiceConfiguration,
        protocolFactory: ProtocolFactory,
        context: Context
    ): AndroidRouter {
        return AndroidRouter(
            configuration,
            protocolFactory,
            context
        )
    }
    override fun onDestroy() {
        binder.registry.shutdown()
        binder.configuration.shutdown()
    }
}