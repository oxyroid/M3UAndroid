package com.m3u.dlna.android

import org.jupnp.UpnpService
import org.jupnp.UpnpServiceConfiguration
import org.jupnp.controlpoint.ControlPoint
import org.jupnp.registry.Registry

interface AndroidUpnpService {
    val service: UpnpService
    val configuration: UpnpServiceConfiguration
    val registry: Registry
    val controlPoint: ControlPoint
}