package com.m3u.extension.transport.android

import org.junit.Assert.assertFalse
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
}
