package com.m3u.features.console.command

import com.m3u.data.source.scanner.dlna.UpnpRegistryImpl
import org.fourthline.cling.model.meta.Device

class UpnpCommandHandler(
    input: String
) : CommandHandler(
    input = input,
    key = "upnp"
) {
    init {
        super.configPath("discover") { param ->
            val output = CommandResource.Output(scanner(param))
            send(output)
            send(CommandResource.Idle)
        }
    }

    override val introduce: String = """
        Welcome to Upnp Command Handler
        - discover [local/remote]: Start a scanner to search devices.
        ~
    """.trimIndent()

    private suspend fun scanner(param: String? = null): String {
        val scanner = UpnpRegistryImpl()
        return when (param) {
            null -> scanner("local") + "\n" + scanner("remote")
            "local" -> "Local Devices: " + scanner.localDevices().displayString
            "remote" -> "Remote Devices: " + scanner.remoteDevices().displayString
            else -> introduce
        }
    }
}

private val List<Device<*, *, *>>.displayString: String
    get() = joinToString(
        prefix = "\n",
        separator = "\n",
        postfix = "\n~"
    ) { "${it.displayString}, ${it.type.displayString}" }
