package com.m3u.core.architecture.logger

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_ACTIVITIES
import android.os.Build
import com.m3u.core.util.collection.forEachNotNull
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@Retention(AnnotationRetention.BINARY)
@Deprecated("This is an android platform implement.")
annotation class FileLoggerImpl

/**
 * An uncaught error file collector.
 * Write messages to application cache dir.
 *
 * TODO: This implement is an android platform version.
 */
class FileLogger(
    private val context: Context
) : Logger {
    private val packageInfo: PackageInfo
        get() {
            val packageManager = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(GET_ACTIVITIES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, GET_ACTIVITIES)
            }
        }

    private val dir: File = context.cacheDir

    private val PackageInfo.code: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            versionCode.toString()
        }

    private fun readSystemConfiguration(): Map<String, String> = buildMap {
        Build::class.java.declaredFields.forEachNotNull { field ->
            try {
                field.isAccessible = true
                val key = field.name
                val value = field.get(null)?.toString().orEmpty()
                put(key, value)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun getStackTraceMessage(throwable: Throwable): String {
        val writer = StringWriter()
        val printer = PrintWriter(writer)
        throwable.printStackTrace(printer)
        var cause = throwable.cause
        while (cause != null) {
            cause.printStackTrace(printer)
            cause = cause.cause
        }
        printer.close()
        return writer.toString()
    }

    private fun Map<*, *>.joinToString(): String = buildString {
        entries.forEach {
            appendLine("${it.key} = ${it.value}")
        }
    }

    private fun writeToFile(text: String) {
        val file = File(dir.path, "${System.currentTimeMillis()}.txt")
        if (!file.exists()) {
            file.createNewFile()
        }
        file.writeText(text)
    }

    private fun writeInfoToFile(text: String) {
        val file = File(dir.path, "info.txt")
        if (!file.exists()) {
            file.createNewFile()
        }
        file.appendText("[${System.currentTimeMillis()}] $text")
    }

    override fun log(throwable: Throwable) {
        val infoMap = mutableMapOf<String, String>()
        infoMap["name"] = packageInfo.versionName
        infoMap["code"] = packageInfo.code

        readSystemConfiguration().forEach(infoMap::put)

        val info = infoMap.joinToString()
        val trace = getStackTraceMessage(throwable)

        val text = buildString {
            appendLine(info)
            appendLine(trace)
        }
        writeToFile(text)
    }

    override fun log(text: String) {
        writeInfoToFile(text)
    }
}