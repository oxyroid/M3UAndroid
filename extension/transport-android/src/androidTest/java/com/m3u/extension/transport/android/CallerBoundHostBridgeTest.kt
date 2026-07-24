package com.m3u.extension.transport.android

import android.content.Context
import android.os.DeadObjectException
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import com.m3u.extension.transport.android.ipc.IExtensionResultCallback
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallerBoundHostBridgeTest {
    @Test
    fun callerFromAnotherUidIsRejectedBeforeDelegation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var delegated = false
        val delegate = object : IExtensionHostBridge.Stub() {
            override fun executeHttp(
                requestId: String,
                request: ParcelFileDescriptor,
                callback: IExtensionResultCallback,
            ) {
                delegated = true
                ParcelFileCodec.write(context, "{}").use { response ->
                    callback.onSuccess(requestId, response)
                }
            }

            override fun cancelHttp(requestId: String?) = Unit
        }
        val bridge = RevocableExtensionHostBridge(
            delegate = delegate,
            expectedUid = Process.myUid() + 1,
            requestPermits = Semaphore(1),
        )

        assertThrows(SecurityException::class.java) {
            bridge.executeHttp(
                "request",
                ParcelFileCodec.write(context, "{}"),
                object : IExtensionResultCallback.Stub() {
                    override fun onSuccess(
                        requestId: String,
                        response: ParcelFileDescriptor,
                    ) {
                        response.close()
                    }

                    override fun onFailure(
                        requestId: String,
                        code: String,
                        message: String,
                    ) = Unit
                },
            )
        }

        assertFalse(delegated)
    }

    @Test
    fun closeContinuesReleasingRequestsWhenCallbacksThrow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requestPermits = Semaphore(2)
        val delegate = object : IExtensionHostBridge.Stub() {
            override fun executeHttp(
                requestId: String?,
                request: ParcelFileDescriptor?,
                callback: IExtensionResultCallback?,
            ) {
                request?.close()
            }

            override fun cancelHttp(requestId: String?) = Unit
        }
        val bridge = RevocableExtensionHostBridge(
            delegate = delegate,
            expectedUid = Process.myUid(),
            requestPermits = requestPermits,
        )
        val callbackAttempts = AtomicInteger()

        repeat(2) { index ->
            bridge.executeHttp(
                "request-$index",
                ParcelFileCodec.write(context, "{}"),
                object : IExtensionResultCallback.Stub() {
                    override fun onSuccess(
                        requestId: String?,
                        response: ParcelFileDescriptor?,
                    ) {
                        response?.close()
                    }

                    override fun onFailure(
                        requestId: String?,
                        code: String?,
                        message: String?,
                    ) {
                        callbackAttempts.incrementAndGet()
                        throw DeadObjectException()
                    }
                },
            )
        }

        bridge.close()

        assertEquals(2, callbackAttempts.get())
        assertEquals(2, requestPermits.availablePermits())
    }

    @Test
    fun invalidNullableArgumentsAreRejectedBeforeDelegation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var delegated = false
        val delegate = object : IExtensionHostBridge.Stub() {
            override fun executeHttp(
                requestId: String?,
                request: ParcelFileDescriptor?,
                callback: IExtensionResultCallback?,
            ) {
                delegated = true
            }

            override fun cancelHttp(requestId: String?) = Unit
        }
        val bridge = RevocableExtensionHostBridge(
            delegate = delegate,
            expectedUid = Process.myUid(),
            requestPermits = Semaphore(1),
        )
        val request = ParcelFileCodec.write(context, "{}")

        bridge.executeHttp(null, request, null)

        assertFalse(delegated)
        assertThrows(java.io.IOException::class.java) {
            ParcelFileCodec.read(request, 16)
        }
    }
}
