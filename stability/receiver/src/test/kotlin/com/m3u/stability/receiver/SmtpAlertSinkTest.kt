package com.m3u.stability.receiver

import jakarta.mail.Message
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class SmtpAlertSinkTest {
    @Test
    fun `email is addressed to the fixed crash inbox`() {
        val sink = SmtpAlertSink(
            AlertConfiguration.Smtp(
                host = "smtp.example.test",
                port = 587,
                username = "sender",
                password = "server-only-secret",
                from = "alerts@example.test",
            )
        )
        val message = sink.createMessage(
            StoredAlert(
                id = "alert-id",
                type = AlertType.NEW_ISSUE,
                subject = "[M3U crash][new] v1.15.1 332bfa24",
                body = "Sanitized stack trace",
                createdAtEpochMillis = Instant.parse("2026-08-03T10:00:00Z").toEpochMilli(),
            )
        )

        assertEquals(
            CRASH_ALERT_RECIPIENT,
            message.getRecipients(Message.RecipientType.TO).single().toString(),
        )
        assertEquals("alerts@example.test", message.from.single().toString())
    }
}
