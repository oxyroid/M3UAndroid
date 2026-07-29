package com.m3u.data.repository

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Process-local, bounded JSONL staging for records that must be fully produced before a database
 * transaction starts. Encoding and decoding stay with the caller so this container can also stage
 * programme or other serializable records without depending on their models.
 */
internal class BoundedJsonlRecordStaging<T> private constructor(
    private val file: File,
    private val limits: Limits,
    private val encode: (T) -> String,
    private val decode: (String) -> T,
) : Closeable {
    private val fileOutput = FileOutputStream(file)
    private val output = BufferedOutputStream(fileOutput, IO_BUFFER_BYTES)
    private var state = State.WRITING
    private var encodedBytes = 0L

    var recordCount: Int = 0
        private set

    suspend fun append(record: T): Int {
        check(state == State.WRITING) { "Staging is no longer writable" }
        currentCoroutineContext().ensureActive()
        if (recordCount >= limits.maximumRecords) {
            throw IOException("Staging contains too many records")
        }
        val encoded = encode(record)
        require('\n' !in encoded && '\r' !in encoded) {
            "A staged JSON record must occupy exactly one line"
        }
        val bytes = encoded.encodeToByteArray()
        if (bytes.size > limits.maximumRecordBytes) {
            throw IOException("Staging record exceeds the size limit")
        }
        val requiredBytes = bytes.size.toLong() + 1L
        if (encodedBytes > limits.maximumBytes - requiredBytes) {
            throw IOException("Staging exceeds the total size limit")
        }
        currentCoroutineContext().ensureActive()
        output.write(bytes)
        output.write(LINE_FEED.toInt())
        encodedBytes += requiredBytes
        recordCount++
        return recordCount
    }

    fun seal() {
        check(state == State.WRITING) { "Staging cannot be sealed in its current state" }
        try {
            output.flush()
            fileOutput.fd.sync()
            state = State.SEALED
        } finally {
            output.close()
        }
    }

    suspend fun forEachBatch(
        batchSize: Int,
        action: suspend (List<T>) -> Unit,
    ) {
        require(batchSize > 0) { "Batch size must be positive" }
        check(state == State.SEALED) { "Staging must be sealed before it is read" }
        var decodedRecords = 0
        var batch = ArrayList<T>(minOf(batchSize, recordCount))
        file.bufferedReader(Charsets.UTF_8, IO_BUFFER_BYTES).use { input ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = input.readLine() ?: break
                decodedRecords++
                if (decodedRecords > recordCount) {
                    throw IOException("Staging contains more records than were written")
                }
                batch += decode(line)
                if (batch.size == batchSize) {
                    action(batch)
                    batch = ArrayList(minOf(batchSize, recordCount - decodedRecords))
                }
            }
        }
        if (decodedRecords != recordCount) {
            throw IOException("Staging record count changed before import")
        }
        if (batch.isNotEmpty()) {
            action(batch)
        }
    }

    override fun close() {
        if (state == State.CLOSED) return
        state = State.CLOSED
        runCatching { output.close() }
        release(file)
    }

    data class Limits(
        val maximumRecords: Int,
        val maximumBytes: Long,
        val maximumRecordBytes: Int,
    ) {
        init {
            require(maximumRecords >= 0) { "Maximum record count must not be negative" }
            require(maximumBytes >= 0L) { "Maximum staging size must not be negative" }
            require(maximumRecordBytes >= 0) {
                "Maximum record size must not be negative"
            }
        }
    }

    private enum class State {
        WRITING,
        SEALED,
        CLOSED,
    }

    companion object {
        private val activePaths = mutableSetOf<String>()

        @Synchronized
        fun <T> create(
            cacheDirectory: File,
            limits: Limits,
            encode: (T) -> String,
            decode: (String) -> T,
        ): BoundedJsonlRecordStaging<T> {
            check(cacheDirectory.exists() || cacheDirectory.mkdirs()) {
                "Unable to create the staging directory"
            }
            cleanup(cacheDirectory)
            val file = File.createTempFile(FILE_PREFIX, FILE_SUFFIX, cacheDirectory)
            activePaths += file.absolutePath
            return try {
                BoundedJsonlRecordStaging(
                    file = file,
                    limits = limits,
                    encode = encode,
                    decode = decode,
                )
            } catch (error: Throwable) {
                activePaths -= file.absolutePath
                file.delete()
                throw error
            }
        }

        @Synchronized
        internal fun cleanup(cacheDirectory: File) {
            cacheDirectory.listFiles()
                .orEmpty()
                .asSequence()
                .filter { file ->
                    file.name.startsWith(FILE_PREFIX) &&
                        file.name.endsWith(FILE_SUFFIX) &&
                        file.absolutePath !in activePaths
                }
                .forEach(::deleteOrTruncate)
        }

        @Synchronized
        private fun release(file: File) {
            activePaths -= file.absolutePath
            deleteOrTruncate(file)
        }

        private fun deleteOrTruncate(file: File) {
            if (file.exists() && !file.delete()) {
                runCatching { file.writeBytes(byteArrayOf()) }
            }
        }

        private const val FILE_PREFIX = "bounded-jsonl-"
        private const val FILE_SUFFIX = ".staging"
        private const val IO_BUFFER_BYTES = 32 * 1024
        private const val LINE_FEED: Byte = '\n'.code.toByte()
    }
}
