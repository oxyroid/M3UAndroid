package com.m3u.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.GET_ACTIVITIES
import android.os.Build
import com.m3u.core.architecture.Logger
import com.m3u.core.util.collection.forEachNotNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

class FileLogger @Inject constructor(
    @ApplicationContext private val context: Context
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

    private val dir: File? = context.externalCacheDir

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
        dir?.let {
            val file = File(it.path, "${System.currentTimeMillis()}.txt")
            if (!file.exists()) {
                file.createNewFile()
            }
            file.writeText(text)
        }
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

    override fun readAll(): List<String> = dir?.let {
        val dir = File(it.path)
        if (!dir.exists() || dir.isFile) return@let emptyList()
        (dir.list() ?: emptyArray()).map { filePath ->
            val file = try {
                File(filePath)
            } catch (ignored: NullPointerException) {
                null
            }
            file?.readText().orEmpty()
        }
    } ?: emptyList()
}