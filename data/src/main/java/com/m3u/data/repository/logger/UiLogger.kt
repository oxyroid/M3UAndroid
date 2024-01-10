package com.m3u.data.repository.logger

import com.m3u.core.architecture.logger.Logger
import com.m3u.core.wrapper.Message
import com.m3u.data.service.DynamicMessageService
import java.util.Locale
import javax.inject.Inject
import kotlin.time.Duration

class UiLogger @Inject constructor(
    private val dynamicMessageService: DynamicMessageService,
    private val logger: Logger
) : Logger {
    override fun log(
        text: String,
        level: Int,
        tag: String,
        duration: Duration
    ) {
        val value = text.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT)
            else it.toString()
        }
        val message = Message.Dynamic(
            value = value,
            level = level,
            tag = tag,
            type = Message.TYPE_SNACK,
            duration = duration
        )
        dynamicMessageService.emit(message)
    }

    override fun log(
        throwable: Throwable,
        tag: String
    ) {
        val info = throwable.stackTraceToString()
        throwable.message?.let(::log)
        logger.log(
            """
            ${throwable.message}
            $info
            =====
            """.trimIndent(),
            tag = tag
        )
    }
}
