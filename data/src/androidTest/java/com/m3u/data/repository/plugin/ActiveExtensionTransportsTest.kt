package com.m3u.data.repository.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveExtensionTransportsTest {
    @Test
    fun sameExtensionIdDoesNotMergeDifferentServices() {
        val transports = ActiveExtensionTransports<String>()
        val first = ExtensionServiceKey("com.example.first", "FirstService")
        val second = ExtensionServiceKey("com.example.second", "SecondService")

        transports.put(first, EXTENSION_ID, "first-transport")
        transports.put(second, EXTENSION_ID, "second-transport")

        assertEquals("first-transport", transports[first]?.transport)
        assertEquals("second-transport", transports[second]?.transport)
    }

    @Test
    fun removeMissingOnlyRemovesServicesThatAreNoLongerInstalled() {
        val transports = ActiveExtensionTransports<String>()
        val installed = ExtensionServiceKey("com.example.installed", "ExtensionService")
        val removed = ExtensionServiceKey("com.example.removed", "ExtensionService")
        transports.put(installed, "installed-extension", "installed-transport")
        transports.put(removed, "removed-extension", "removed-transport")

        val removedTransports = transports.removeMissing(setOf(installed))

        assertEquals(listOf("removed-transport"), removedTransports.map { it.transport })
        assertEquals("installed-transport", transports[installed]?.transport)
        assertNull(transports[removed])
    }

    @Test
    fun removeByExtensionIdLeavesOtherExtensionsConnected() {
        val transports = ActiveExtensionTransports<String>()
        val target = ExtensionServiceKey("com.example.target", "ExtensionService")
        val other = ExtensionServiceKey("com.example.other", "ExtensionService")
        transports.put(target, EXTENSION_ID, "target-transport")
        transports.put(other, "com.example.other.extension", "other-transport")

        val removed = transports.removeByExtensionId(EXTENSION_ID)

        assertEquals(listOf("target-transport"), removed.map { it.transport })
        assertNull(transports[target])
        assertEquals("other-transport", transports[other]?.transport)
    }

    private companion object {
        const val EXTENSION_ID = "com.example.shared.extension"
    }
}
