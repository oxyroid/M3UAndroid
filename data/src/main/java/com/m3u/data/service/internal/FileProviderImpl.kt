package com.m3u.data.service.internal

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.m3u.core.architecture.FileProvider
import com.m3u.core.util.collections.forEachNotNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

class FileProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FileProvider {
    private val dir = context.cacheDir
    override fun readAll(): List<File> {
        if (!dir.exists() || dir.isFile) return emptyList()
        return dir.list()
            ?.filter { it.endsWith(".txt") }
            ?.map { File(it) }
            ?: emptyList()
    }

    override fun read(path: String): File? {
        if (!dir.exists() || dir.isFile) return null
        return dir
            .listFiles { file -> file.absolutePath.endsWith(path) }
            ?.firstOrNull()
    }

    override fun write(value: Throwable) {
        val infoMap = mutableMapOf<String, String>()
        infoMap["name"] = packageInfo.versionName
        infoMap["code"] = packageInfo.code

        readConfiguration().forEach(infoMap::put)

        val info = infoMap.joinToString()
        val trace = getStackTraceMessage(value)

        val text = buildString {
            appendLine(info)
            appendLine(trace)
        }
        writeToFile(text)
    }

    private val packageInfo: PackageInfo
        get() {
            val packageManager = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong())
                )
            } else {
                packageManager.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
            }
        }

    private val PackageInfo.code: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            versionCode.toString()
        }

    private fun readConfiguration(): Map<String, String> = buildMap {
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
}