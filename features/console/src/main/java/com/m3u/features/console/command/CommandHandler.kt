package com.m3u.features.console.command

import com.m3u.features.console.command.impl.EmptyCommandHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal abstract class CommandHandler(private val input: String) {
    protected abstract val introduce: String
    private val scopes: MutableMap<String, suspend CommandScope.() -> Unit> = mutableMapOf()

    protected fun path(
        path: String,
        block: suspend CommandScope.() -> Unit = {},
    ) {
        scopes[path] = block
    }

    open fun execute(): Flow<CommandResource<String>> = channelFlow {
        val commands = input.split(" ")
        val path = commands.path ?: run {
            send(CommandResource.Output(introduce))
            send(CommandResource.Idle)
            return@channelFlow
        }
        val scope = CommandProducerScope(this, commands)
        val block = scopes[path]
        if (block != null) {
            block(scope)
            if (!scope.keep) {
                send(CommandResource.Idle)
            }
        } else {
            EmptyCommandHandler(input)
                .execute()
                .onEach(::send)
                .launchIn(this)
        }
    }

    companion object {
        fun parseKey(input: String): String? {
            return input.lowercase().split(" ").firstOrNull()
        }
    }
}

private val List<String>.path: String? get() = getOrNull(1)