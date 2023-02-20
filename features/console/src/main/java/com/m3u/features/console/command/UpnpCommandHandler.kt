package com.m3u.features.console.command

import com.m3u.data.source.scanner.dlna.UpnpRegistryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.fourthline.cling.model.meta.Device

class UpnpCommandHandler(
    private val input: String
) : CommandHandler() {
    private var next: CommandHandler? = null
    override fun intercept(handler: CommandHandler?) {
        val commands = input.split(" ")
        if (commands.isEmpty()) {
            next = EmptyCommandHandler
            return
        }
        val key = commands.key
        if ((key != "upnp")) {
            next = EmptyCommandHandler
            return
        }
        next = handler
    }

    override fun execute(): Flow<CommandResource<String>> = channelFlow {
        when (val n = next) {
            null -> {
                val commands = input.split(" ")
                val path = commands.path ?: run {
                    send(CommandResource.Output(introduce))
                    send(CommandResource.Idle)
                    return@channelFlow
                }
                val resource = CommandResource.Output("Executing Upnp Command...")
                send(resource)
                when (path) {
                    "discover" -> {
                        val param = commands.param
                        val output = CommandResource.Output(scanner(param))
                        send(output)
                        send(CommandResource.Idle)
                    }
                    else -> introduce
                }
            }
            else -> n
                .execute()
                .onEach(::send)
                .launchIn(this)
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

private val List<String>.key: String get() = first()
private val List<String>.path: String? get() = getOrNull(1)
private val List<String>.param: String? get() = getOrNull(2)

private val List<Device<*, *, *>>.displayString: String get() = joinToString(separator = "\n") { it.displayString }
