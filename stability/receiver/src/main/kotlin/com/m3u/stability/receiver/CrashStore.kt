package com.m3u.stability.receiver

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val STORE_SCHEMA_VERSION = 1
private const val RETENTION_DAYS = 30L
private const val SPIKE_WINDOW_MINUTES = 15L
private const val SPIKE_THRESHOLD = 5
private const val MAX_PENDING_ALERTS = 1_000

internal class CrashStore(
    private val stateFile: Path,
    private val clock: Clock = Clock.systemUTC(),
    private val maxPendingAlerts: Int = MAX_PENDING_ALERTS,
) {
    private val lock = Any()
    private var state: StoreState = loadState()

    init {
        require(maxPendingAlerts > 0) { "maxPendingAlerts must be positive" }
        verifyStateDirectoryWritable()
    }

    fun record(report: NormalizedCrashReport): RecordResult = synchronized(lock) {
        val now = clock.millis()
        state = state.withExpiredIssuesRemoved(now)
        if (report.reportId in state.reportIds) {
            return@synchronized RecordResult(
                issueKey = "${report.fingerprint}:${report.versionCode}",
                alertType = null,
                duplicate = true,
            )
        }
        val issueKey = "${report.fingerprint}:${report.versionCode}"
        val existing = state.issues[issueKey]
        val previousReleaseIssue = state.issues.values
            .filter { issue -> issue.fingerprint == report.fingerprint }
            .maxByOrNull(StoredIssue::lastSeenAtEpochMillis)
        val windowStart = existing?.windowStartAtEpochMillis
            ?.takeIf { start -> now - start < SPIKE_WINDOW_MINUTES.minutesInMillis() }
            ?: now
        val windowCount = if (windowStart == existing?.windowStartAtEpochMillis) {
            existing.windowCount + 1
        } else {
            1
        }
        val updated = StoredIssue(
            key = issueKey,
            fingerprint = report.fingerprint,
            versionCode = report.versionCode,
            versionName = report.versionName,
            exceptionType = report.exceptionType,
            firstSeenAtEpochMillis = existing?.firstSeenAtEpochMillis ?: now,
            lastSeenAtEpochMillis = now,
            totalCount = (existing?.totalCount ?: 0L) + 1L,
            windowStartAtEpochMillis = windowStart,
            windowCount = windowCount,
            spikeAlertedForWindowStart = existing?.spikeAlertedForWindowStart
                ?.takeIf { it == windowStart },
            latestReport = report,
        )
        val newIssues = state.issues + (issueKey to updated)
        val alertType = when {
            existing == null && previousReleaseIssue == null -> AlertType.NEW_ISSUE
            existing == null -> AlertType.REGRESSION
            windowCount >= SPIKE_THRESHOLD && updated.spikeAlertedForWindowStart != windowStart ->
                AlertType.SPIKE
            else -> null
        }
        val alert = alertType?.let { type -> updated.toAlert(type, now) }
        val finalIssue = if (alertType == AlertType.SPIKE) {
            updated.copy(spikeAlertedForWindowStart = windowStart)
        } else {
            updated
        }
        val enqueueResult = alert?.let { state.pendingAlerts.enqueue(it, maxPendingAlerts) }
        state = state.copy(
            issues = newIssues + (issueKey to finalIssue),
            pendingAlerts = enqueueResult?.alerts ?: state.pendingAlerts,
            reportIds = state.reportIds + (report.reportId to now),
            acceptedReportCount = state.acceptedReportCount + 1L,
            lastAcceptedAtEpochMillis = now,
            droppedAlertCount = state.droppedAlertCount +
                if (enqueueResult?.dropped == true) 1L else 0L,
            lastDroppedAlertAtEpochMillis = if (enqueueResult?.dropped == true) {
                now
            } else {
                state.lastDroppedAlertAtEpochMillis
            },
        )
        persistState()
        RecordResult(issueKey = issueKey, alertType = alertType, duplicate = false)
    }

    fun enqueueDeliveryTest(): StoredAlert? = synchronized(lock) {
        val now = clock.millis()
        state = state.withExpiredIssuesRemoved(now)
        val alertId = UUID.randomUUID().toString()
        val alert = StoredAlert(
            id = alertId,
            type = AlertType.DELIVERY_TEST,
            subject = "[M3U crash][test] ${alertId.take(8)}",
            body = buildString {
                appendLine("event: delivery_test")
                appendLine("probe_id: $alertId")
                appendLine("queued_at: ${Instant.ofEpochMilli(now)}")
                appendLine("recipient: $CRASH_ALERT_RECIPIENT")
                append("This message verifies the crash alert delivery path.")
            },
            createdAtEpochMillis = now,
        )
        val enqueueResult = state.pendingAlerts.enqueue(alert, maxPendingAlerts)
        state = state.copy(
            pendingAlerts = enqueueResult.alerts,
            droppedAlertCount = state.droppedAlertCount +
                if (enqueueResult.dropped) 1L else 0L,
            lastDroppedAlertAtEpochMillis = if (enqueueResult.dropped) {
                now
            } else {
                state.lastDroppedAlertAtEpochMillis
            },
        )
        persistState()
        alert.takeIf { enqueueResult.incomingAccepted }
    }

    fun nextPendingAlert(): StoredAlert? = synchronized(lock) {
        val now = clock.millis()
        state.pendingAlerts
            .asSequence()
            .filter { alert -> alert.nextAttemptAtEpochMillis <= now }
            .minByOrNull(StoredAlert::createdAtEpochMillis)
    }

    fun markAlertSent(alertId: String) = synchronized(lock) {
        state = state.copy(
            pendingAlerts = state.pendingAlerts.filterNot { alert -> alert.id == alertId },
            deliveredAlertCount = state.deliveredAlertCount + 1L,
            lastAlertDeliveredAtEpochMillis = clock.millis(),
            lastAlertFailureType = null,
        )
        persistState()
    }

    fun markAlertFailed(alertId: String, failure: Throwable) = synchronized(lock) {
        val now = clock.millis()
        state = state.copy(
            pendingAlerts = state.pendingAlerts.map { alert ->
                if (alert.id != alertId) {
                    alert
                } else {
                    val attempts = alert.attempts + 1
                    alert.copy(
                        attempts = attempts,
                        nextAttemptAtEpochMillis = now + retryDelayMillis(attempts),
                    )
                }
            },
            lastAlertFailureAtEpochMillis = now,
            lastAlertFailureType = failure.javaClass.simpleName.take(128),
        )
        persistState()
    }

    fun status(): ReceiverStatus = synchronized(lock) {
        ReceiverStatus(
            issueCount = state.issues.size,
            pendingAlertCount = state.pendingAlerts.size,
            acceptedReportCount = state.acceptedReportCount,
            deliveredAlertCount = state.deliveredAlertCount,
            droppedAlertCount = state.droppedAlertCount,
            lastAcceptedAtEpochMillis = state.lastAcceptedAtEpochMillis,
            lastAlertDeliveredAtEpochMillis = state.lastAlertDeliveredAtEpochMillis,
            lastAlertFailureAtEpochMillis = state.lastAlertFailureAtEpochMillis,
            lastAlertFailureType = state.lastAlertFailureType,
            lastDroppedAlertAtEpochMillis = state.lastDroppedAlertAtEpochMillis,
        )
    }

    private fun loadState(): StoreState {
        if (!stateFile.exists()) return StoreState()
        val loaded = runCatching {
            storeJson.decodeFromString<StoreState>(stateFile.readText())
        }.getOrElse { failure ->
            error("Crash receiver state is unreadable at $stateFile: ${failure.javaClass.simpleName}")
        }
        check(loaded.schemaVersion == STORE_SCHEMA_VERSION) {
            "Unsupported crash receiver state schema ${loaded.schemaVersion}"
        }
        return loaded
    }

    private fun persistState() {
        val parent = stateFile.toAbsolutePath().parent
        parent?.createDirectories()
        val temporary = Files.createTempFile(parent, ".crash-state-", ".json")
        try {
            temporary.writeText(storeJson.encodeToString(state))
            runCatching {
                Files.setPosixFilePermissions(temporary, ownerOnlyPermissions)
            }
            try {
                Files.move(
                    temporary,
                    stateFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, stateFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun verifyStateDirectoryWritable() {
        val parent = stateFile.toAbsolutePath().parent
        parent?.createDirectories()
        val probe = Files.createTempFile(parent, ".crash-state-probe-", ".tmp")
        Files.deleteIfExists(probe)
    }
}

internal data class RecordResult(
    val issueKey: String,
    val alertType: AlertType?,
    val duplicate: Boolean,
)

@Serializable
internal data class ReceiverStatus(
    val issueCount: Int,
    val pendingAlertCount: Int,
    val acceptedReportCount: Long,
    val deliveredAlertCount: Long,
    val droppedAlertCount: Long,
    val lastAcceptedAtEpochMillis: Long?,
    val lastAlertDeliveredAtEpochMillis: Long?,
    val lastAlertFailureAtEpochMillis: Long?,
    val lastAlertFailureType: String?,
    val lastDroppedAlertAtEpochMillis: Long?,
)

@Serializable
internal enum class AlertType {
    NEW_ISSUE,
    REGRESSION,
    SPIKE,
    DELIVERY_TEST,
}

@Serializable
internal data class StoredAlert(
    val id: String,
    val type: AlertType,
    val subject: String,
    val body: String,
    val createdAtEpochMillis: Long,
    val attempts: Int = 0,
    val nextAttemptAtEpochMillis: Long = createdAtEpochMillis,
)

@Serializable
private data class StoreState(
    val schemaVersion: Int = STORE_SCHEMA_VERSION,
    val issues: Map<String, StoredIssue> = emptyMap(),
    val pendingAlerts: List<StoredAlert> = emptyList(),
    val reportIds: Map<String, Long> = emptyMap(),
    val acceptedReportCount: Long = 0L,
    val deliveredAlertCount: Long = 0L,
    val droppedAlertCount: Long = 0L,
    val lastAcceptedAtEpochMillis: Long? = null,
    val lastAlertDeliveredAtEpochMillis: Long? = null,
    val lastAlertFailureAtEpochMillis: Long? = null,
    val lastAlertFailureType: String? = null,
    val lastDroppedAlertAtEpochMillis: Long? = null,
)

@Serializable
private data class StoredIssue(
    val key: String,
    val fingerprint: String,
    val versionCode: Long,
    val versionName: String,
    val exceptionType: String,
    val firstSeenAtEpochMillis: Long,
    val lastSeenAtEpochMillis: Long,
    val totalCount: Long,
    val windowStartAtEpochMillis: Long,
    val windowCount: Int,
    val spikeAlertedForWindowStart: Long? = null,
    val latestReport: NormalizedCrashReport,
)

private fun StoreState.withExpiredIssuesRemoved(now: Long): StoreState {
    val retentionStart = now - RETENTION_DAYS.daysInMillis()
    val retainedIssues = issues.filterValues { issue ->
        issue.lastSeenAtEpochMillis >= retentionStart
    }
    return copy(
        issues = retainedIssues,
        pendingAlerts = pendingAlerts.filter { alert -> alert.createdAtEpochMillis >= retentionStart },
        reportIds = reportIds.filterValues { receivedAt -> receivedAt >= retentionStart },
    )
}

private fun StoredIssue.toAlert(type: AlertType, now: Long): StoredAlert {
    val title = when (type) {
        AlertType.NEW_ISSUE -> "new"
        AlertType.REGRESSION -> "regression"
        AlertType.SPIKE -> "spike"
        AlertType.DELIVERY_TEST -> error("Delivery tests are not issue alerts")
    }
    return StoredAlert(
        id = UUID.randomUUID().toString(),
        type = type,
        subject = "[M3U crash][$title] v$versionName ${fingerprint.take(12)}",
        body = buildString {
            appendLine("event: $title")
            appendLine("received_at: ${Instant.ofEpochMilli(now)}")
            appendLine("issue: $key")
            appendLine("version: $versionName ($versionCode)")
            appendLine("exception: $exceptionType")
            appendLine("total_count: $totalCount")
            appendLine("window_count: $windowCount")
            appendLine("feature: ${latestReport.feature ?: "unknown"}")
            appendLine("previous_exit: ${latestReport.previousExit ?: "none"}")
            appendLine("device: ${latestReport.phoneModel ?: "unknown"}")
            appendLine("android: ${latestReport.androidVersion ?: "unknown"}")
            appendLine()
            appendLine("Sanitized stack trace:")
            append(latestReport.stackTrace)
        },
        createdAtEpochMillis = now,
    )
}

private fun retryDelayMillis(attempts: Int): Long {
    val exponent = (attempts - 1).coerceIn(0, 7)
    return (30_000L shl exponent).coerceAtMost(6L * 60L * 60L * 1_000L)
}

private fun Long.minutesInMillis(): Long = this * 60L * 1_000L
private fun Long.daysInMillis(): Long = this * 24L * 60L * 60L * 1_000L

private data class AlertEnqueueResult(
    val alerts: List<StoredAlert>,
    val dropped: Boolean,
    val incomingAccepted: Boolean,
)

private fun List<StoredAlert>.enqueue(
    alert: StoredAlert,
    capacity: Int,
): AlertEnqueueResult {
    if (size < capacity) {
        return AlertEnqueueResult(
            alerts = this + alert,
            dropped = false,
            incomingAccepted = true,
        )
    }
    if (alert.type == AlertType.SPIKE || alert.type == AlertType.DELIVERY_TEST) {
        return AlertEnqueueResult(
            alerts = this,
            dropped = true,
            incomingAccepted = false,
        )
    }
    val replaceIndex = indexOfFirst { queued -> queued.type == AlertType.SPIKE }
        .takeIf { index -> index >= 0 }
        ?: indices.minBy { index -> this[index].createdAtEpochMillis }
    return AlertEnqueueResult(
        alerts = toMutableList().apply { set(replaceIndex, alert) },
        dropped = true,
        incomingAccepted = true,
    )
}

private val storeJson = Json {
    prettyPrint = true
    explicitNulls = false
    encodeDefaults = true
}

private val ownerOnlyPermissions = setOf(
    PosixFilePermission.OWNER_READ,
    PosixFilePermission.OWNER_WRITE,
)
