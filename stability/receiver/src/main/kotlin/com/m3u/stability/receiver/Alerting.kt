package com.m3u.stability.receiver

import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory

internal const val CRASH_ALERT_RECIPIENT = "crash@oxyroid.com"

internal fun interface AlertSink {
    suspend fun send(alert: StoredAlert)
}

internal class StdoutAlertSink : AlertSink {
    override suspend fun send(alert: StoredAlert) {
        println(alert.subject)
        println(alert.body)
    }
}

internal class SmtpAlertSink(
    configuration: AlertConfiguration.Smtp,
) : AlertSink {
    private val from = InternetAddress(configuration.from, true)
    private val session = Session.getInstance(
        Properties().apply {
            setProperty("mail.smtp.host", configuration.host)
            setProperty("mail.smtp.port", configuration.port.toString())
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.starttls.enable", "true")
            setProperty("mail.smtp.starttls.required", "true")
            setProperty("mail.smtp.ssl.checkserveridentity", "true")
            setProperty("mail.smtp.connectiontimeout", "5000")
            setProperty("mail.smtp.timeout", "10000")
            setProperty("mail.smtp.writetimeout", "10000")
        },
        object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(configuration.username, configuration.password)
        },
    )

    override suspend fun send(alert: StoredAlert) = withContext(Dispatchers.IO) {
        Transport.send(createMessage(alert))
    }

    internal fun createMessage(alert: StoredAlert): MimeMessage = MimeMessage(session).apply {
        setFrom(this@SmtpAlertSink.from)
        setRecipient(Message.RecipientType.TO, InternetAddress(CRASH_ALERT_RECIPIENT, true))
        setSubject(alert.subject, Charsets.UTF_8.name())
        setText(alert.body, Charsets.UTF_8.name())
    }
}

internal class AlertDispatcher(
    private val store: CrashStore,
    private val sink: AlertSink,
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(AlertDispatcher::class.java)
    private val flushMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        pollingJob = scope.launch {
            while (isActive) {
                flushSafely()
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    fun requestFlush() {
        if (!started.get()) return
        scope.launch { flushSafely() }
    }

    private suspend fun flushSafely() {
        runCatching { flushAvailable() }
            .onFailure { failure ->
                logger.error("Crash alert dispatch failed: {}", failure.javaClass.simpleName)
            }
    }

    internal suspend fun flushAvailable() {
        flushMutex.withLock {
            repeat(MAX_ALERTS_PER_FLUSH) {
                val alert = store.nextPendingAlert() ?: return@withLock
                val result = runCatching { sink.send(alert) }
                result.fold(
                    onSuccess = { store.markAlertSent(alert.id) },
                    onFailure = { failure ->
                        store.markAlertFailed(alert.id, failure)
                        return@withLock
                    },
                )
            }
        }
    }

    override fun close() {
        pollingJob?.cancel()
        scope.cancel()
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 30_000L
        const val MAX_ALERTS_PER_FLUSH = 20
    }
}
