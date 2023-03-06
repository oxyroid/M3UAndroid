package com.m3u.features.console.command.impl

import com.m3u.features.console.command.CommandHandler

internal object EmptyCommandHandler : CommandHandler("", "") {
    override val introduce: String = ""
}