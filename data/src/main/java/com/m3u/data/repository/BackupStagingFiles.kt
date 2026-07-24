package com.m3u.data.repository

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal object BackupStagingFiles {
    private val activePaths = mutableSetOf<String>()

    @Synchronized
    fun create(cacheDirectory: File): File {
        cleanup(cacheDirectory)
        return File.createTempFile(FILE_PREFIX, FILE_SUFFIX, cacheDirectory).also { file ->
            activePaths += file.absolutePath
        }
    }

    @Synchronized
    fun release(file: File) {
        activePaths -= file.absolutePath
        if (file.exists() && !file.delete()) {
            runCatching { file.writeBytes(byteArrayOf()) }
        }
    }

    @Synchronized
    fun cleanup(cacheDirectory: File) {
        cacheDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { file ->
                file.name.startsWith(FILE_PREFIX) &&
                    file.name.endsWith(FILE_SUFFIX) &&
                    file.absolutePath !in activePaths
            }
            .forEach { file ->
                if (!file.delete()) {
                    runCatching { file.writeBytes(byteArrayOf()) }
                }
            }
    }

    suspend fun copyBounded(
        input: InputStream,
        destination: File,
        maximumBytes: Long = MAX_BACKUP_BYTES,
    ) {
        require(maximumBytes >= 0) { "Maximum backup size must not be negative" }
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            var totalBytes = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                if (totalBytes > maximumBytes - count) {
                    throw IOException("Backup exceeds the restore size limit")
                }
                output.write(buffer, 0, count)
                totalBytes += count
            }
        }
    }

    suspend fun forEachLine(
        file: File,
        maximumLineBytes: Int = MAX_BACKUP_LINE_BYTES,
        maximumLines: Int = MAX_BACKUP_LINES,
        action: suspend (String) -> Unit,
    ) {
        require(maximumLineBytes >= 0) {
            "Maximum backup record size must not be negative"
        }
        require(maximumLines >= 0) {
            "Maximum backup record count must not be negative"
        }
        file.inputStream().buffered().use { input ->
            val readBuffer = ByteArray(COPY_BUFFER_BYTES)
            val lineBuffer = ByteArrayOutputStream()
            var lineCount = 0

            suspend fun emitLine() {
                currentCoroutineContext().ensureActive()
                lineCount++
                if (lineCount > maximumLines) {
                    throw IOException("Backup contains too many records")
                }
                val encoded = lineBuffer.toByteArray()
                val contentSize = if (encoded.lastOrNull() == CARRIAGE_RETURN) {
                    encoded.size - 1
                } else {
                    encoded.size
                }
                action(
                    encoded.decodeToString(
                        startIndex = 0,
                        endIndex = contentSize,
                        throwOnInvalidSequence = true,
                    )
                )
                lineBuffer.reset()
            }

            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(readBuffer)
                if (count < 0) break
                if (count == 0) continue
                var segmentStart = 0
                repeat(count) { index ->
                    if (readBuffer[index] == LINE_FEED) {
                        appendBounded(
                            target = lineBuffer,
                            source = readBuffer,
                            offset = segmentStart,
                            length = index - segmentStart,
                            maximumBytes = maximumLineBytes,
                        )
                        emitLine()
                        segmentStart = index + 1
                    }
                }
                appendBounded(
                    target = lineBuffer,
                    source = readBuffer,
                    offset = segmentStart,
                    length = count - segmentStart,
                    maximumBytes = maximumLineBytes,
                )
            }
            if (lineBuffer.size() > 0) emitLine()
        }
    }

    private fun appendBounded(
        target: ByteArrayOutputStream,
        source: ByteArray,
        offset: Int,
        length: Int,
        maximumBytes: Int,
    ) {
        if (target.size() > maximumBytes - length) {
            throw IOException("Backup record exceeds the line size limit")
        }
        target.write(source, offset, length)
    }

    private const val FILE_PREFIX = "m3u-restore-"
    private const val FILE_SUFFIX = ".backup"
    private const val COPY_BUFFER_BYTES = 32 * 1024
    private const val MAX_BACKUP_LINE_BYTES = 1024 * 1024
    private const val MAX_BACKUP_LINES = 2_000_000
    private const val MAX_BACKUP_BYTES = 512L * 1024L * 1024L
    private const val CARRIAGE_RETURN: Byte = '\r'.code.toByte()
    private const val LINE_FEED: Byte = '\n'.code.toByte()
}
