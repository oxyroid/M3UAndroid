package com.m3u.extension.transport.android

import java.io.Closeable
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InvocationBridgeLeaseTest {
    @Test
    fun `closing before bridge creation rejects and closes the late bridge`() {
        val lease = InvocationBridgeLease()
        val bridge = RecordingCloseable()

        lease.close()

        assertFalse(lease.attach(bridge))
        assertTrue(bridge.closed)
    }

    @Test
    fun `closing an attached bridge is idempotent`() {
        val lease = InvocationBridgeLease()
        val bridge = RecordingCloseable()
        assertTrue(lease.attach(bridge))

        lease.close()
        lease.close()

        assertTrue(bridge.closed)
    }

    private class RecordingCloseable : Closeable {
        var closed = false

        override fun close() {
            closed = true
        }
    }
}
