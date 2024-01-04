package com.m3u.features.console.command.impl

import com.m3u.core.architecture.FilePathCacher
import com.m3u.features.console.command.ParamCommandHandler

internal class LoggerCommandHandler(
    input: String,
    cacher: FilePathCacher
) : ParamCommandHandler(input) {
    init {
        path("list") {
            val files = cacher.readAll()
            val text = if (param.isNullOrEmpty()) {
                files.joinToString(
                    separator = "\n",
                    postfix = "\n",
                    transform = { it.path }
                )
            } else {
                val file = when (param) {
                    "-i" -> files.lastOrNull()
                    else -> files.find { it.path == arg }
                }
                file?.readText() ?: ""
            }
            output(text)
        }
    }

    override val introduce: String
        get() = """
            Welcome to Logger Command Handler
            - list: show all *.log files as a directory.
            - list -l: show content of latest log file.
            - list log.txt: show content of target log file.
            ~
        """.trimIndent()

    companion object {
        const val KEY = "logger"
    }
}