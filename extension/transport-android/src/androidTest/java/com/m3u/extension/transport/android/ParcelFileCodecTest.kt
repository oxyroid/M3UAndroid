package com.m3u.extension.transport.android

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.SystemClock
import android.os.storage.StorageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ParcelFileCodecTest {
    @Test
    fun readsRegularPayload() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val content = ParcelFileCodec.read(ParcelFileCodec.write(context, "payload"), 32)

        assertEquals("payload", content)
    }

    @Test
    fun rejectsPayloadBeyondLimit() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThrows(IllegalArgumentException::class.java) {
            ParcelFileCodec.read(ParcelFileCodec.write(context, "too large"), 3)
        }
    }

    @Test
    fun rejectsWriteBeyondLimitBeforeCreatingDescriptor() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertThrows(IllegalArgumentException::class.java) {
            ParcelFileCodec.write(context, "too large", maximumBytes = 3)
        }
    }

    @Test
    fun rejectsPipeDescriptorsWithoutWaitingForData() {
        val (reader, writer) = ParcelFileDescriptor.createPipe()
        writer.use {
            assertThrows(IOException::class.java) {
                ParcelFileCodec.read(reader, maximumBytes = 32)
            }
        }
    }

    @Test
    fun blockingProxyDescriptorCannotOutliveReadDeadline() {
        val proxy = BlockingProxyDescriptor()
        val startedAt = SystemClock.elapsedRealtime()

        try {
            val failure = assertThrows(IOException::class.java) {
                ParcelFileCodec.read(
                    descriptor = proxy.descriptor,
                    maximumBytes = 32,
                    timeoutMillis = 100,
                )
            }

            assertTrue(failure.message.orEmpty().contains("timed out"))
            assertTrue(SystemClock.elapsedRealtime() - startedAt < 2_000)
            assertTrue(proxy.readStarted.await(1, TimeUnit.SECONDS))
            assertFalse(proxy.descriptorIsOpen())
        } finally {
            proxy.close()
        }
    }

    @Test
    fun interruptingReaderCancelsBlockingProxyAndClosesDescriptor() {
        val proxy = BlockingProxyDescriptor()
        val failure = AtomicReference<Throwable?>()
        val readerWasInterrupted = AtomicBoolean(false)
        val reader = Thread {
            try {
                ParcelFileCodec.read(
                    descriptor = proxy.descriptor,
                    maximumBytes = 32,
                    timeoutMillis = 10_000,
                )
            } catch (caught: Throwable) {
                failure.set(caught)
                readerWasInterrupted.set(Thread.currentThread().isInterrupted)
            }
        }

        try {
            reader.start()
            assertTrue(proxy.readStarted.await(2, TimeUnit.SECONDS))
            reader.interrupt()
            reader.join(2_000)

            assertFalse(reader.isAlive)
            assertTrue(failure.get() is IOException)
            assertTrue(failure.get()?.message.orEmpty().contains("cancelled"))
            assertTrue(readerWasInterrupted.get())
            assertFalse(proxy.descriptorIsOpen())
        } finally {
            proxy.close()
            reader.join(2_000)
        }
    }

    private class BlockingProxyDescriptor {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        private val callbackThread = HandlerThread("blocking-extension-payload").apply { start() }
        private val releaseRead = CountDownLatch(1)
        val readStarted = CountDownLatch(1)
        val descriptor: ParcelFileDescriptor =
            context.getSystemService(StorageManager::class.java).openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                object : ProxyFileDescriptorCallback() {
                    override fun onGetSize(): Long = 1

                    override fun onRead(
                        offset: Long,
                        size: Int,
                        data: ByteArray,
                    ): Int {
                        readStarted.countDown()
                        releaseRead.await()
                        data[0] = 'x'.code.toByte()
                        return 1
                    }

                    override fun onRelease() = Unit
                },
                Handler(callbackThread.looper),
            )

        fun descriptorIsOpen(): Boolean =
            runCatching { descriptor.fileDescriptor.valid() }.getOrDefault(false)

        fun close() {
            releaseRead.countDown()
            runCatching { descriptor.close() }
            callbackThread.quitSafely()
            callbackThread.join(2_000)
        }
    }
}
