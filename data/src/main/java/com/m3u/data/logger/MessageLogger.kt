package com.m3u.data.logger

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Message
import com.m3u.data.service.Messager
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration

class MessageLogger @Inject constructor(
    private val messager: Messager,
    private val logger: Logger
) : Logger {
    override fun log(
        text: String,
        level: Int,
        tag: String,
        duration: Duration,
        type: Int
    ) {
        val value = text.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT)
            else it.toString()
        }
        val message = Message.Dynamic(
            value = value,
            level = level,
            tag = tag,
            type = type,
            duration = duration
        )
        messager.emit(message)
    }

    override fun log(
        throwable: Throwable,
        level: Int,
        tag: String,
    ) {
        val info = throwable.stackTraceToString()
        throwable.message?.let(::log)
        logger.log(
            """
            ${throwable.message}
            $info
            =====
            """.trimIndent(),
            level = level,
            tag = tag,
        )
    }
}
