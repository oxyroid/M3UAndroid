package com.m3u.extension.transport.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import java.io.IOException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExtensionResultDispatcherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reusesOneBinderCallbackAcrossRequests() = runBlocking {
        var firstCallback: IExtensionResultCallback? = null
        ExtensionResultDispatcher().use { dispatcher ->
            val first = dispatcher.await { requestId, callback ->
                firstCallback = callback
                ParcelFileCodec.write(context, "first").use { response ->
                    callback.onSuccess(requestId, response)
                }
            }
            val second = dispatcher.await { requestId, callback ->
                assertSame(firstCallback, callback)
                ParcelFileCodec.write(context, "second").use { response ->
                    callback.onSuccess(requestId, response)
                }
            }

            assertEquals("first", ParcelFileCodec.read(first, 16))
            assertEquals("second", ParcelFileCodec.read(second, 16))
        }
    }

    @Test
    fun pendingResultsAreBoundedAndReleasedAfterCancellation() = runBlocking {
        ExtensionResultDispatcher(maximumPendingResults = 1).use { dispatcher ->
            var requestSent = false
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                dispatcher.await { _, _ -> requestSent = true }
            }
            assertTrue(requestSent)

            val overloaded = runCatching {
                dispatcher.await { _, _ ->
                    throw AssertionError("Overloaded request must not be sent")
                }
            }.exceptionOrNull()
            assertTrue(overloaded is IOException)

            pending.cancel()
            pending.join()

            val result = dispatcher.await { requestId, callback ->
                ParcelFileCodec.write(context, "released").use { response ->
                    callback.onSuccess(requestId, response)
                }
            }
            assertEquals("released", ParcelFileCodec.read(result, 16))
        }
    }

    @Test
    fun cancellationRemovesPendingResultBeforeNotifyingRemote() = runBlocking {
        ExtensionResultDispatcher().use { dispatcher ->
            var sentRequestId = ""
            var cancelledRequestId = ""
            var pendingWhenCancelled = true
            val pending = async(start = CoroutineStart.UNDISPATCHED) {
                dispatcher.await(
                    onCancellation = { requestId ->
                        cancelledRequestId = requestId
                        pendingWhenCancelled = dispatcher.isPending(requestId)
                    },
                ) { requestId, _ ->
                    sentRequestId = requestId
                }
            }

            pending.cancel()
            pending.join()

            assertEquals(sentRequestId, cancelledRequestId)
            assertFalse(pendingWhenCancelled)
            assertFalse(dispatcher.isPending(sentRequestId))
        }
    }

    @Test
    fun nullCallbackPayloadIsClosedAndIgnored() {
        val response = ParcelFileCodec.write(context, "ignored")
        ExtensionResultDispatcher().use { dispatcher ->
            dispatcher.callback.onSuccess(null, response)
        }

        assertThrows(IOException::class.java) {
            ParcelFileCodec.read(response, 16)
        }
    }
}
