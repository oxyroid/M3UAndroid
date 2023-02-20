package com.m3u.features.console.command

object EmptyCommandHandler : CommandHandler(
    input = "",
    key = ""
) {
    override val introduce: String = ""
}