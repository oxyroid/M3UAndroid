package com.m3u.features.console.command.impl

import com.m3u.core.architecture.reader.Reader
import com.m3u.features.console.command.CommandHandler
import java.io.File

internal class LoggerCommandHandler(
    input: String,
    reader: Reader<File>
) : CommandHandler(input) {
    init {
        path("list") {
            val files = reader.read()
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