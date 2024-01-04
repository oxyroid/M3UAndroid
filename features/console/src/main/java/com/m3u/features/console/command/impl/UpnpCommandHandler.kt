package com.m3u.features.console.command.impl

import com.m3u.features.console.command.ParamCommandHandler
import kotlinx.coroutines.flow.Flow

internal class UpnpCommandHandler(
    discoverNearbyDevices: () -> Flow<String>,
    input: String
) : ParamCommandHandler(input) {
    init {
        path("discover") {
            discoverNearbyDevices().collect { device ->
                output(device)
                keep = true
            }
        }
    }

    override val introduce: String = """
        Welcome to Upnp Command Handler
            - discover: discover nearby devices.
    """.trimIndent()

    companion object {
        const val KEY = "upnp"
    }
}