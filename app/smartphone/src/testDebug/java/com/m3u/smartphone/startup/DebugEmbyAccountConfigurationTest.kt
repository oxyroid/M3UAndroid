package com.m3u.smartphone.startup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DebugEmbyAccountConfigurationTest {
    @Test
    fun `unset local fixture is disabled`() {
        assertNull(
            DebugEmbyAccountConfiguration.fromValues(
                baseUrl = "",
                username = "",
                password = "",
            )
        )
    }

    @Test
    fun `configured fixture normalizes non-secret fields`() {
        val fixture = DebugEmbyAccountConfiguration.fromValues(
            baseUrl = " https://media.example.test/ ",
            username = " account ",
            password = " secret with spaces ",
        )

        assertEquals("https://media.example.test", fixture?.baseUrl)
        assertEquals("account", fixture?.username)
        assertEquals(" secret with spaces ", fixture?.password)
    }

    @Test
    fun `partial fixture is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            DebugEmbyAccountConfiguration.fromValues(
                baseUrl = "https://media.example.test",
                username = "account",
                password = "",
            )
        }
    }

    @Test
    fun `fixture rejects credentials embedded in a server URL`() {
        assertFailsWith<IllegalArgumentException> {
            DebugEmbyAccountConfiguration.fromValues(
                baseUrl = "https://account:secret@media.example.test",
                username = "account",
                password = "secret",
            )
        }
    }
}
