package com.m3u.core.architecture.reader

import android.content.Context
import java.io.File

class AndroidFileReader constructor(
    context: Context
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