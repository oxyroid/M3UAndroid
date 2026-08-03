package com.m3u.stability.receiver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ReceiverConfigurationTest {
    @Test
    fun `startup requires an explicit alert mode`() {
        assertFailsWith<IllegalStateException> {
            ReceiverConfiguration.fromEnvironment(emptyMap())
        }
    }

    @Test
    fun `stdout mode is available for local validation`() {
        val configuration = ReceiverConfiguration.fromEnvironment(
            mapOf("M3U_CRASH_ALERT_MODE" to "stdout")
        )

        assertIs<AlertConfiguration.Stdout>(configuration.alertConfiguration)
        assertEquals(8080, configuration.port)
    }

    @Test
    fun `smtp mode keeps the alert destination fixed`() {
        val configuration = ReceiverConfiguration.fromEnvironment(
            mapOf(
                "M3U_CRASH_ALERT_MODE" to "smtp",
                "M3U_CRASH_ADMIN_TOKEN" to "a".repeat(32),
                "M3U_CRASH_SMTP_HOST" to "smtp.example.test",
                "M3U_CRASH_SMTP_USERNAME" to "sender",
                "M3U_CRASH_SMTP_PASSWORD" to "server-only-secret",
                "M3U_CRASH_SMTP_FROM" to "alerts@example.test",
            )
        )

        val smtp = assertIs<AlertConfiguration.Smtp>(configuration.alertConfiguration)
        assertEquals("smtp.example.test", smtp.host)
        assertEquals(587, smtp.port)
        assertEquals("crash@oxyroid.com", CRASH_ALERT_RECIPIENT)
    }

    @Test
    fun `short admin tokens fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            ReceiverConfiguration.fromEnvironment(
                mapOf(
                    "M3U_CRASH_ALERT_MODE" to "stdout",
                    "M3U_CRASH_ADMIN_TOKEN" to "too-short",
                )
            )
        }
    }
}
