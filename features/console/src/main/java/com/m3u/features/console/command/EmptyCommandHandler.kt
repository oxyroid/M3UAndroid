package com.m3u.features.console.command

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

object EmptyCommandHandler : CommandHandler() {
    override fun intercept(handler: CommandHandler?) {}
    override fun execute(): Flow<CommandResource<String>> = flow {}
    override val introduce: String = ""
}