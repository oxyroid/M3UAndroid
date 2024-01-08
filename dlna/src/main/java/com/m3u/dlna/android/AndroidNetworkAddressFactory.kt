package com.m3u.dlna.android

import org.jupnp.transport.impl.NetworkAddressFactoryImpl
import java.lang.reflect.Field
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

class AndroidNetworkAddressFactory(
    streamListenPort: Int,
    multicastResponsePort: Int
) : NetworkAddressFactoryImpl(streamListenPort, multicastResponsePort) {
    override fun requiresNetworkInterface(): Boolean = false
    override fun isUsableAddress(
        networkInterface: NetworkInterface?,
        address: InetAddress
    ): Boolean {
        val result = super.isUsableAddress(networkInterface, address)
        if (result) {
            val hostName = address.hostAddress

            var field: Field? = null
            var target: Any? = null

            try {
                try {
                    field = InetAddress::class.java.getDeclaredField("holder")
                    field.isAccessible = true
                    target = field.get(address)
                    field = target.javaClass.getDeclaredField("hostName")
                } catch (e: NoSuchFieldException) {
                    // Let's try the non-OpenJDK variant
                    field = InetAddress::class.java.getDeclaredField("hostName")
                    target = address
                }

                if (field != null && target != null && hostName != null) {
                    field.isAccessible = true
                    field.set(target, hostName)
                } else {
                    return false
                }
            } catch (ex: Exception) {
                return false
            }
        }
        return result
    }

    override fun getLocalAddress(
        networkInterface: NetworkInterface?,
        isIPv6: Boolean,
        remoteAddress: InetAddress?
    ): InetAddress {
        for (address in getInetAddresses(networkInterface)) {
            if (isIPv6 && address is Inet6Address) return address
            if (!isIPv6 && address is Inet4Address) return address
        }
        throw IllegalStateException("Can't find any IPv4 or IPv6 address on interface: " + networkInterface?.displayName)
    }

    override fun discoverNetworkInterfaces() {
        try {
            super.discoverNetworkInterfaces()
        } catch (e: Exception) {
            super.discoverNetworkInterfaces()
        }
    }
}