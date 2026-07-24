package com.m3u.extension.transport.android

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

object ParcelFileCodec {
    fun write(
        context: Context,
        content: String,
        maximumBytes: Int = Int.MAX_VALUE,
    ): ParcelFileDescriptor {
        require(maximumBytes > 0) { "Extension payload limit must be positive" }
        val encoded = content.encodeToByteArray()
        require(encoded.size <= maximumBytes) {
            "Extension payload exceeds transport limit"
        }
        val file = File.createTempFile("extension-", ".json", context.cacheDir)
        file.writeBytes(encoded)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        file.delete()
        return descriptor
    }

    fun read(
        descriptor: ParcelFileDescriptor,
        maximumBytes: Int,
        timeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    ): String {
        require(maximumBytes in 0 until Int.MAX_VALUE) {
            "Extension payload limit is invalid"
        }
        require(timeoutMillis > 0) { "Extension payload timeout must be positive" }
        val read = try {
            READ_EXECUTOR.submit(Callable { readFully(descriptor, maximumBytes) })
        } catch (failure: RejectedExecutionException) {
            runCatching { descriptor.close() }
            throw IOException("Extension payload readers are at capacity", failure)
        }
        return try {
            read.get(timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (failure: TimeoutException) {
            abortRead(descriptor, read)
            throw IOException("Extension payload read timed out", failure)
        } catch (failure: InterruptedException) {
            abortRead(descriptor, read)
            Thread.currentThread().interrupt()
            throw IOException("Extension payload read was cancelled", failure)
        } catch (failure: CancellationException) {
            runCatching { descriptor.close() }
            throw IOException("Extension payload read was cancelled", failure)
        } catch (failure: ExecutionException) {
            when (val cause = failure.cause) {
                is IOException -> throw cause
                is RuntimeException -> throw cause
                is Error -> throw cause
                else -> throw IOException("Unable to read extension payload", cause)
            }
        }
    }

    suspend fun readInterruptibly(
        descriptor: ParcelFileDescriptor,
        maximumBytes: Int,
        timeoutMillis: Long = DEFAULT_READ_TIMEOUT_MILLIS,
    ): String = runInterruptible(Dispatchers.IO) {
        read(
            descriptor = descriptor,
            maximumBytes = maximumBytes,
            timeoutMillis = timeoutMillis,
        )
    }

    private fun readFully(
        descriptor: ParcelFileDescriptor,
        maximumBytes: Int,
    ): String = descriptor.use { current ->
        val fileDescriptor = current.fileDescriptor
        val stat = try {
            Os.fstat(fileDescriptor)
        } catch (failure: ErrnoException) {
            throw IOException("Unable to inspect extension payload", failure)
        }
        if (stat.st_mode and OsConstants.S_IFMT != OsConstants.S_IFREG) {
            throw IOException("Extension payload descriptor must be a regular file")
        }
        if (stat.st_size < 0L) {
            throw IOException("Extension payload size is invalid")
        }
        require(stat.st_size <= maximumBytes.toLong()) {
            "Extension payload exceeds transport limit"
        }

        val payload = ByteArray(stat.st_size.toInt())
        var offset = 0
        while (offset < payload.size) {
            val count = try {
                Os.pread(
                    fileDescriptor,
                    payload,
                    offset,
                    payload.size - offset,
                    offset.toLong(),
                )
            } catch (failure: ErrnoException) {
                if (failure.errno == OsConstants.EINTR) {
                    continue
                } else {
                    throw IOException("Unable to read extension payload", failure)
                }
            }
            if (count == 0) {
                throw IOException("Extension payload was truncated while being read")
            }
            offset += count
        }
        payload.decodeToString()
    }

    private fun abortRead(
        descriptor: ParcelFileDescriptor,
        read: java.util.concurrent.Future<String>,
    ) {
        runCatching { descriptor.close() }
        read.cancel(true)
    }

    private const val DEFAULT_READ_TIMEOUT_MILLIS = 5_000L
    private const val MAX_PROCESS_READERS = 4
    private const val IDLE_READER_TTL_SECONDS = 30L
    private val READER_NUMBER = AtomicInteger()
    private val READ_EXECUTOR = ThreadPoolExecutor(
        0,
        MAX_PROCESS_READERS,
        IDLE_READER_TTL_SECONDS,
        TimeUnit.SECONDS,
        SynchronousQueue(),
        ThreadFactory { task ->
            Thread(task, "extension-payload-reader-${READER_NUMBER.incrementAndGet()}").apply {
                isDaemon = true
            }
        },
        ThreadPoolExecutor.AbortPolicy(),
    )
}
