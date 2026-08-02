package com.m3u.smartphone.stability

import java.util.Collections
import java.util.IdentityHashMap

/** Formats diagnostic stack frames without exception messages, which may contain user data. */
internal object SafeStackTraceFormatter {
    fun format(throwable: Throwable?): String {
        if (throwable == null) return "Unknown crash\n"

        val visited = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        return buildString {
            appendThrowable(throwable, caption = null, prefix = "", visited = visited)
        }
    }

    private fun StringBuilder.appendThrowable(
        throwable: Throwable,
        caption: String?,
        prefix: String,
        visited: MutableSet<Throwable>,
    ) {
        if (!visited.add(throwable)) {
            append(prefix).append("[circular reference]\n")
            return
        }

        append(prefix)
        caption?.let(::append)
        append(throwable.javaClass.name).append('\n')
        throwable.stackTrace.forEach { frame ->
            append(prefix).append("\tat ").append(frame).append('\n')
        }
        throwable.suppressed.forEach { suppressed ->
            appendThrowable(
                throwable = suppressed,
                caption = "Suppressed: ",
                prefix = "$prefix\t",
                visited = visited,
            )
        }
        throwable.cause?.let { cause ->
            appendThrowable(
                throwable = cause,
                caption = "Caused by: ",
                prefix = prefix,
                visited = visited,
            )
        }
    }
}
