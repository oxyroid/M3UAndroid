package com.m3u.data.logger

import android.util.Log
import com.m3u.core.architecture.Publisher
import com.m3u.core.architecture.Logger
import javax.inject.Inject

/**
 * This is a wrapper of android logcat.
 * Its tag is always be "Logger".
 * You can only output info and error level logs.
 * And it only be effective when package is debug mode.
 *
 * This is the default Logger implement.
 */
class CommonLogger @Inject constructor(
    @Publisher.App private val publisher: Publisher
) : Logger {
    override fun log(text: String) {
        if (publisher.debug) {
            Log.i("Logger", text)
        }
    }

    override fun log(throwable: Throwable) {
        if (publisher.debug) {
            Log.e("Logger", "", throwable)
        }
    }
}

object Level {
    const val V = 10
    const val D = 20
    const val I = 30
    const val W = 40
    const val E = 50
}

class Logger(private val tag: String) {
    companion object {
        var enabled: Boolean = true
        var level: Int = Level.I
        var printThread: Boolean = false

        var printer: Printer = Printer { level, tag, message, throwable ->
            if (throwable != null) {
                when (level) {
                    Level.V -> Log.v(tag, message.toString(), throwable)
                    Level.D -> Log.d(tag, message.toString(), throwable)
                    Level.I -> Log.i(tag, message.toString(), throwable)
                    Level.W -> Log.w(tag, message.toString(), throwable)
                    Level.E -> Log.e(tag, message.toString(), throwable)
                }
            } else {
                when (level) {
                    Level.V -> Log.v(tag, message.toString())
                    Level.D -> Log.d(tag, message.toString())
                    Level.I -> Log.i(tag, message.toString())
                    Level.W -> Log.w(tag, message.toString())
                    Level.E -> Log.e(tag, message.toString())
                }
            }
        }

        fun create(tag: String) = Logger(tag)
    }

    fun v(message: CharSequence, throwable: Throwable? = null) {
        if (enabled && Level.V >= level) {
            printer.print(Level.V, getTag(), message, throwable)
        }
    }

    fun v(throwable: Throwable?, function: () -> CharSequence) {
        if (enabled && Level.V >= level) {
            printer.print(Level.V, getTag(), function(), throwable)
        }
    }

    fun d(message: CharSequence, throwable: Throwable? = null) {
        if (enabled && Level.D >= level) {
            printer.print(Level.D, getTag(), message, throwable)
        }
    }

    fun d(throwable: Throwable?, function: () -> CharSequence) {
        if (enabled && Level.D >= level) {
            printer.print(Level.D, getTag(), function(), throwable)
        }
    }

    fun i(message: CharSequence, throwable: Throwable? = null) {
        if (enabled && Level.I >= level) {
            printer.print(Level.I, getTag(), message, throwable)
        }
    }

    fun i(throwable: Throwable?, function: () -> CharSequence) {
        if (enabled && Level.I >= level) {
            printer.print(Level.I, getTag(), function(), throwable)
        }
    }

    fun w(message: CharSequence, throwable: Throwable? = null) {
        if (enabled && Level.W >= level) {
            printer.print(Level.W, getTag(), message, throwable)
        }
    }

    fun w(throwable: Throwable?, function: () -> CharSequence) {
        if (enabled && Level.W >= level) {
            printer.print(Level.W, getTag(), function(), throwable)
        }
    }

    fun e(message: CharSequence, throwable: Throwable? = null) {
        if (enabled && Level.E >= level) {
            printer.print(Level.E, getTag(), message, throwable)
        }
    }

    fun e(throwable: Throwable?, function: () -> CharSequence) {
        if (enabled && Level.E >= level) {
            printer.print(Level.E, getTag(), function(), throwable)
        }
    }

    private fun getTag(): String {
        return tag + (if (printThread) "[${Thread.currentThread().name}]" else "")
    }

    fun interface Printer {
        fun print(level: Int, tag: String, message: CharSequence, throwable: Throwable?)
    }
}
