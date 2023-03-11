package com.m3u.features.console.command

import kotlinx.coroutines.channels.ProducerScope

internal interface CommandScope {
    val param: String?
    val arg: String?
    suspend fun output(text: String)
}

internal suspend fun CommandScope.error(text: String) {
    val message = text.lines().joinToString(
        separator = "\n",
        transform = { "!-$it" }
    )
    output(message)
}

internal class CommandProducerScope(
    private val scope: ProducerScope<CommandResource<String>>,
    commands: List<String>,
) : CommandScope {
    override val param: String? = commands.param
    override val arg: String? = commands.arg
    override suspend fun output(text: String) {
        scope.send(CommandResource.Output(text))
    }
}

private val List<String>.param: String? get() = getOrNull(2)
private val List<String>.arg: String? get() = getOrNull(3)
