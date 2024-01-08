package com.m3u.dlna.android

import android.os.Build
import org.jupnp.DefaultUpnpServiceConfiguration
import org.jupnp.binding.xml.DeviceDescriptorBinder
import org.jupnp.binding.xml.RecoveringUDA10DeviceDescriptorBinderImpl
import org.jupnp.binding.xml.ServiceDescriptorBinder
import org.jupnp.binding.xml.UDA10ServiceDescriptorBinderSAXImpl
import org.jupnp.model.Namespace
import org.jupnp.model.ServerClientTokens
import org.jupnp.transport.impl.GENAEventProcessorImpl
import org.jupnp.transport.impl.SOAPActionProcessorImpl
import org.jupnp.transport.impl.ServletStreamServerConfigurationImpl
import org.jupnp.transport.impl.ServletStreamServerImpl
import org.jupnp.transport.impl.jetty.JettyServletContainer
import org.jupnp.transport.impl.jetty.JettyStreamClientImpl
import org.jupnp.transport.impl.jetty.StreamClientConfigurationImpl
import org.jupnp.transport.spi.GENAEventProcessor
import org.jupnp.transport.spi.NetworkAddressFactory
import org.jupnp.transport.spi.SOAPActionProcessor
import org.jupnp.transport.spi.StreamClient
import org.jupnp.transport.spi.StreamServer

open class AndroidUpnpServiceConfiguration(
    streamListenPort: Int = 0,
    multicastResponsePort: Int = 0
) : DefaultUpnpServiceConfiguration(streamListenPort, multicastResponsePort, false) {
    init {
        System.setProperty("org.xml.sax.driver", "org.xmlpull.v1.sax2.Driver")
    }

    override fun createNetworkAddressFactory(
        streamListenPort: Int,
        multicastResponsePort: Int
    ): NetworkAddressFactory {
        return AndroidNetworkAddressFactory(streamListenPort, multicastResponsePort)
    }

    override fun createNamespace(): Namespace = Namespace("/upnp")

    override fun createStreamClient(): StreamClient<*> {
        val configuration = object : StreamClientConfigurationImpl(syncProtocolExecutorService) {
            override fun getUserAgentValue(majorVersion: Int, minorVersion: Int): String {
                val tokens = ServerClientTokens(majorVersion, minorVersion).apply {
                    osName = "Android"
                    osVersion = Build.VERSION.RELEASE
                }
                return tokens.toString()
            }
        }
        return JettyStreamClientImpl(configuration)
    }

    override fun createStreamServer(networkAddressFactory: NetworkAddressFactory): StreamServer<*> {
        return ServletStreamServerImpl(
            ServletStreamServerConfigurationImpl(
                JettyServletContainer.INSTANCE,
                networkAddressFactory.streamListenPort
            )
        )
    }

    override fun createDeviceDescriptorBinderUDA10(): DeviceDescriptorBinder {
        return RecoveringUDA10DeviceDescriptorBinderImpl()
    }

    override fun createServiceDescriptorBinderUDA10(): ServiceDescriptorBinder {
        return UDA10ServiceDescriptorBinderSAXImpl()
    }

    override fun createSOAPActionProcessor(): SOAPActionProcessor {
        return SOAPActionProcessorImpl()
    }

    override fun createGENAEventProcessor(): GENAEventProcessor {
        return GENAEventProcessorImpl()
    }

    override fun getRegistryMaintenanceIntervalMillis(): Int {
        return 3000
    }
}