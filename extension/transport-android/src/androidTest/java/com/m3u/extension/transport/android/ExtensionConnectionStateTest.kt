package com.m3u.extension.transport.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionConnectionStateTest {
    @Test
    fun connectionIsUnavailableUntilBindingCompletes() {
        val state = ExtensionConnectionState()

        assertFalse(state.isAvailable)

        state.markAvailable()

        assertTrue(state.isAvailable)
    }

    @Test
    fun disconnectMakesAnAvailableConnectionUnavailable() {
        val state = ExtensionConnectionState()
        state.markAvailable()

        state.markUnavailable()

        assertFalse(state.isAvailable)
    }

    @Test
    fun disconnectNotifiesTheListenerOnlyOnce() {
        val state = ExtensionConnectionState()
        var notifications = 0
        state.markAvailable()
        state.setUnavailableListener { notifications += 1 }

        state.markUnavailable()
        state.markUnavailable()

        assertEquals(1, notifications)
    }
}
