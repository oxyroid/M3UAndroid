package com.m3u.stability.receiver

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CrashStoreTest {
    @Test
    fun `persists new issue spike and release regression alerts without duplicates`() {
        val directory = createTempDirectory("m3u-crash-store-test")
        try {
            val clock = MutableClock(Instant.parse("2026-08-03T10:00:00Z"))
            val stateFile = directory.resolve("state.json")
            val store = CrashStore(stateFile, clock)
            var reportSequence = 1
            assertEquals(
                AlertType.NEW_ISSUE,
                store.record(report(reportId = "probe-${reportSequence++}")).alertType,
            )
            val newIssue = assertNotNull(store.nextPendingAlert())
            assertEquals(AlertType.NEW_ISSUE, newIssue.type)
            store.markAlertSent(newIssue.id)

            repeat(3) {
                assertNull(store.record(report(reportId = "probe-${reportSequence++}")).alertType)
            }
            assertEquals(
                AlertType.SPIKE,
                store.record(report(reportId = "probe-${reportSequence++}")).alertType,
            )
            val spike = assertNotNull(store.nextPendingAlert())
            assertEquals(AlertType.SPIKE, spike.type)
            store.markAlertSent(spike.id)
            assertNull(store.record(report(reportId = "probe-${reportSequence++}")).alertType)

            clock.advanceSeconds(60)
            val regression = store.record(
                report(
                    reportId = "probe-${reportSequence++}",
                    versionCode = 146L,
                    versionName = "1.15.2",
                )
            )
            assertEquals(AlertType.REGRESSION, regression.alertType)

            val reloaded = CrashStore(stateFile, clock)
            assertEquals(7L, reloaded.status().acceptedReportCount)
            assertEquals(1, reloaded.status().pendingAlertCount)
            assertEquals(AlertType.REGRESSION, reloaded.nextPendingAlert()?.type)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `failed delivery remains queued and records only the failure type`() = runBlocking {
        val directory = createTempDirectory("m3u-crash-alert-test")
        try {
            val clock = MutableClock(Instant.parse("2026-08-03T10:00:00Z"))
            val store = CrashStore(directory.resolve("state.json"), clock)
            store.record(report())
            val dispatcher = AlertDispatcher(store) { error("smtp password=must-not-be-stored") }

            dispatcher.flushAvailable()

            val status = store.status()
            assertEquals(1, status.pendingAlertCount)
            assertEquals("IllegalStateException", status.lastAlertFailureType)
            assertTrue(directory.resolve("state.json").toFile().readText().contains("IllegalStateException"))
            assertTrue(!directory.resolve("state.json").toFile().readText().contains("must-not-be-stored"))
            dispatcher.close()
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a retried report id is acknowledged without changing counts or alerts`() {
        val directory = createTempDirectory("m3u-crash-dedup-test")
        try {
            val store = CrashStore(directory.resolve("state.json"))
            val report = report(reportId = "stable-report-id")
            assertTrue(!store.record(report).duplicate)
            val originalStatus = store.status()

            assertTrue(store.record(report).duplicate)

            assertEquals(originalStatus, store.status())
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `delivery test is persisted with a unique probe id`() {
        val directory = createTempDirectory("m3u-crash-delivery-test")
        try {
            val stateFile = directory.resolve("state.json")
            val alert = assertNotNull(CrashStore(stateFile).enqueueDeliveryTest())

            assertEquals(AlertType.DELIVERY_TEST, alert.type)
            assertTrue(alert.subject.contains(alert.id.take(8)))
            assertTrue(alert.body.contains("probe_id: ${alert.id}"))
            assertTrue(alert.body.contains("recipient: $CRASH_ALERT_RECIPIENT"))
            assertEquals(alert.id, CrashStore(stateFile).nextPendingAlert()?.id)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `a full queue records loss and keeps the newest high priority alert`() {
        val directory = createTempDirectory("m3u-crash-queue-test")
        try {
            val clock = MutableClock(Instant.parse("2026-08-03T10:00:00Z"))
            val store = CrashStore(
                stateFile = directory.resolve("state.json"),
                clock = clock,
                maxPendingAlerts = 2,
            )
            store.record(report(reportId = "first", fingerprint = "11111111"))
            clock.advanceSeconds(1)
            store.record(report(reportId = "second", fingerprint = "22222222"))
            clock.advanceSeconds(1)
            store.record(report(reportId = "third", fingerprint = "33333333"))

            val status = store.status()
            assertEquals(2, status.pendingAlertCount)
            assertEquals(1L, status.droppedAlertCount)
            assertEquals(clock.millis(), status.lastDroppedAlertAtEpochMillis)
            assertTrue(store.nextPendingAlert()?.subject?.contains("22222222") == true)
            assertNull(store.enqueueDeliveryTest())
            assertEquals(2L, store.status().droppedAlertCount)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun report(
        reportId: String = "probe",
        versionCode: Long = 145L,
        versionName: String = "1.15.1",
        fingerprint: String = "332bfa24",
    ): NormalizedCrashReport = NormalizedCrashReport(
        reportId = reportId,
        versionCode = versionCode,
        versionName = versionName,
        fingerprint = fingerprint,
        exceptionType = "java.lang.IllegalStateException",
        stackTrace = "java.lang.IllegalStateException\n\tat Probe.run(Probe.kt:1)",
        feature = "app",
        buildType = "debug",
        previousExit = null,
        phoneModel = "Pixel 6 Pro",
        androidVersion = "16",
        crashDate = "2026-08-03T10:00:00Z",
        silent = false,
    )
}

private class MutableClock(
    private var instant: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = instant

    fun advanceSeconds(seconds: Long) {
        instant = instant.plusSeconds(seconds)
    }
}
