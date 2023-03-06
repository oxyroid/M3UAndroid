package com.m3u.features.console.command

import androidx.annotation.CallSuper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

internal abstract class CommandHandler(
    private val input: String,
    private val key: String,
) {
    protected abstract val introduce: String
    private val scopes: MutableMap<String, suspend CommandScope.() -> Unit> = mutableMapOf()

    protected fun path(
        path: String,
        block: suspend CommandScope.() -> Unit = {},
    ) {
        scopes[path] = block
    }

    @CallSuper
    open fun execute(): Flow<CommandResource<String>> = channelFlow {
        val commands = input.split(" ")
        val path = commands.path ?: run {
            send(CommandResource.Output(introduce))
            send(CommandResource.Idle)
            return@channelFlow
        }
        val block = scopes[path]
        if (block != null) {
            val resource = CommandResource.Output("Executing $key command...")
            send(resource)
            val scope = CommandProducerScope(this, commands)
            block(scope)
        } else {
            val resource =
                CommandResource.Output("Unknown path \"$path\", please try again!")
            send(resource)
            send(CommandResource.Idle)
        }
    }

    companion object {
        fun parseKey(input: String): String? {
            return input.lowercase().split(" ").firstOrNull()
        }
    }
}

private val List<String>.path: String? get() = getOrNull(1)