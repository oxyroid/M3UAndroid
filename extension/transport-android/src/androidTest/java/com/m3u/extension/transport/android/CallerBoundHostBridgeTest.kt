package com.m3u.extension.transport.android

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m3u.extension.transport.android.ipc.IExtensionHostBridge
import java.util.concurrent.Semaphore
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
            override fun executeHttp(request: ParcelFileDescriptor): ParcelFileDescriptor {
                delegated = true
                return ParcelFileCodec.write(context, "{}")
            }
        }
        val bridge = RevocableExtensionHostBridge(
            delegate = delegate,
            expectedUid = Process.myUid() + 1,
            requestPermits = Semaphore(1),
        )

        assertThrows(SecurityException::class.java) {
            bridge.executeHttp(ParcelFileCodec.write(context, "{}"))
        }

        assertFalse(delegated)
    }
}
