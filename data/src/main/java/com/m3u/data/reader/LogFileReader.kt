package com.m3u.data.reader

import android.content.Context
import com.m3u.core.architecture.FileReader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class LogFileReader @Inject constructor(
    @ApplicationContext context: Context
) : FileReader {
    private val dir = context.cacheDir
    override fun read(): List<File> {
        val dir = File(dir.path)
        if (!dir.exists() || dir.isFile) return emptyList()
        return dir.list()
            ?.filter { it.endsWith(".txt") }
            ?.map { File(it) }
            ?: emptyList()
    }
}