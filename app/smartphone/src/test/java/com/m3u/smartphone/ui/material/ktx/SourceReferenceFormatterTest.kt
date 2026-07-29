package com.m3u.smartphone.ui.material.ktx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceReferenceFormatterTest {
    @Test
    fun `network references expose only their origin`() {
        val reference =
            "https://viewer:secret@example.com:8443/private-token/playlist.m3u" +
                "?username=viewer&password=secret#token"

        val displayed = reference.safeSourceReference()

        assertEquals("https://example.com:8443", displayed)
        listOf("viewer", "secret", "private-token", "playlist.m3u", "username")
            .forEach { sensitiveValue ->
                assertTrue(sensitiveValue !in displayed.orEmpty())
            }
    }

    @Test
    fun `long user info is removed before display text is bounded`() {
        val secret = "s".repeat(600)

        val displayed =
            "https://viewer:$secret@example.com/list.m3u".safeSourceReference()

        assertEquals("https://example.com", displayed)
        assertTrue(secret.take(100) !in displayed.orEmpty())
    }

    @Test
    fun `local references expose only a bounded file name`() {
        assertEquals(
            "channels.m3u",
            "content://media/document/folder/channels.m3u?grant=secret"
                .safeSourceReference(),
        )
        assertEquals(
            "guide.xml",
            "file:///private/folder/guide.xml".safeSourceReference(),
        )
    }

    @Test
    fun `opaque and malformed references stay hidden`() {
        assertNull("provider:private-account".safeSourceReference())
        assertNull("relative/private-token/playlist.m3u".safeSourceReference())
        assertNull(" \u202E ".safeSourceReference())
    }
}
