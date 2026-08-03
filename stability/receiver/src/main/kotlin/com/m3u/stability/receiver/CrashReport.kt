package com.m3u.stability.receiver

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal const val REPORT_SCHEMA = "1"
internal const val REPORT_PACKAGE = "com.m3u.smartphone"
internal const val MAX_COMPRESSED_REPORT_BYTES = 128 * 1024
internal const val MAX_UNCOMPRESSED_REPORT_BYTES = 512 * 1024

private const val MAX_STACK_TRACE_CHARS = 128 * 1024
private const val MAX_FIELD_CHARS = 256

internal sealed interface ReportParseResult {
    data class Accepted(val report: NormalizedCrashReport) : ReportParseResult
    data class Rejected(val reason: String) : ReportParseResult
}

@Serializable
internal data class NormalizedCrashReport(
    val reportId: String,
    val versionCode: Long,
    val versionName: String,
    val fingerprint: String,
    val exceptionType: String,
    val stackTrace: String,
    val feature: String?,
    val buildType: String?,
    val previousExit: String?,
    val phoneModel: String?,
    val androidVersion: String?,
    val crashDate: String?,
    val silent: Boolean?,
)

internal object CrashReportParser {
    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    fun parse(payload: ByteArray): ReportParseResult {
        val report = runCatching {
            json.parseToJsonElement(payload.decodeToString()).jsonObject
        }.getOrElse {
            return ReportParseResult.Rejected("malformed JSON")
        }
        if (!report.keys.containsAll(requiredFields)) {
            return ReportParseResult.Rejected("missing required fields")
        }
        if (report.keys.any { it !in acceptedFields }) {
            return ReportParseResult.Rejected("unknown or forbidden fields")
        }
        if (report.string("PACKAGE_NAME") != REPORT_PACKAGE) {
            return ReportParseResult.Rejected("unexpected package")
        }

        val reportId = report.string("REPORT_ID")
            ?.takeIf(::isUuid)
            ?: return invalid("REPORT_ID")
        val versionCode = report.long("APP_VERSION_CODE")
            ?.takeIf { it > 0L }
            ?: return invalid("APP_VERSION_CODE")
        val versionName = report.string("APP_VERSION_NAME")
            ?.takeIf(safeVersionPattern::matches)
            ?: return invalid("APP_VERSION_NAME")
        val fingerprint = report.string("STACK_TRACE_HASH")
            ?.takeIf(fingerprintPattern::matches)
            ?.lowercase()
            ?: return invalid("STACK_TRACE_HASH")
        val rawStackTrace = report.string("STACK_TRACE")
            ?.takeIf { it.length <= MAX_STACK_TRACE_CHARS }
            ?: return invalid("STACK_TRACE")
        val stackTrace = sanitizeStackTrace(rawStackTrace)
        val exceptionType = stackTrace.lineSequence()
            .map(String::trim)
            .firstOrNull { line -> line.isNotEmpty() && !line.startsWith("at ") }
            ?.removePrefix("Caused by: ")
            ?.removePrefix("Suppressed: ")
            ?.takeIf(exceptionTypePattern::matches)
            ?: return invalid("STACK_TRACE")
        val customData = report["CUSTOM_DATA"] as? JsonObject
            ?: return invalid("CUSTOM_DATA")
        if (customData.keys.any { it !in acceptedCustomDataFields }) {
            return ReportParseResult.Rejected("unknown custom data")
        }

        return ReportParseResult.Accepted(
            NormalizedCrashReport(
                reportId = reportId,
                versionCode = versionCode,
                versionName = versionName,
                fingerprint = fingerprint,
                exceptionType = exceptionType,
                stackTrace = stackTrace,
                feature = customData.string("feature")?.safeToken(),
                buildType = customData.string("build_type")?.safeToken(),
                previousExit = customData.string("previous_exit")?.safeToken(),
                phoneModel = report.string("PHONE_MODEL")?.safeText(),
                androidVersion = report.string("ANDROID_VERSION")?.safeText(),
                crashDate = report.string("USER_CRASH_DATE")?.safeText(),
                silent = report.boolean("IS_SILENT"),
            )
        )
    }

    private fun invalid(field: String): ReportParseResult.Rejected =
        ReportParseResult.Rejected("invalid $field")
}

private fun sanitizeStackTrace(value: String): String = value
    .lineSequence()
    .mapNotNull(::sanitizeStackLine)
    .take(1_000)
    .joinToString("\n")
    .take(MAX_STACK_TRACE_CHARS)

private fun sanitizeStackLine(line: String): String? {
    val trimmed = line.trimEnd()
    if (trimmed.isBlank()) return null
    val leading = trimmed.trimStart()
    return when {
        leading.startsWith("at ") -> leading.removePrefix("at ")
            .takeIf(stackFramePattern::matches)
            ?.let { frame -> "\tat $frame" }
        leading.startsWith("... ") -> leading
            .takeIf(elidedFramePattern::matches)
            ?.let { frame -> "\t$frame" }
        leading.startsWith("Caused by: ") -> leading.removePrefix("Caused by: ")
            .substringBefore(": ")
            .takeIf(exceptionTypePattern::matches)
            ?.let { type -> "Caused by: $type" }
        leading.startsWith("Suppressed: ") -> leading.removePrefix("Suppressed: ")
            .substringBefore(": ")
            .takeIf(exceptionTypePattern::matches)
            ?.let { type -> "Suppressed: $type" }
        leading == "[circular reference]" -> leading
        else -> leading.substringBefore(": ").takeIf(exceptionTypePattern::matches)
    }
}

private fun JsonObject.string(name: String): String? =
    (this[name] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content
        ?.trim()
        ?.takeIf(String::isNotEmpty)

private fun JsonObject.long(name: String): Long? =
    (this[name] as? JsonPrimitive)?.content?.toLongOrNull()

private fun JsonObject.boolean(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

private fun String.safeToken(): String? = takeIf(safeTokenPattern::matches)

private fun String.safeText(): String? = takeIf { value ->
    value.length <= MAX_FIELD_CHARS && value.none(Char::isISOControl)
}

private fun isUuid(value: String): Boolean = runCatching {
    UUID.fromString(value).toString().equals(value, ignoreCase = true)
}.getOrDefault(false)

private val requiredFields = setOf(
    "REPORT_ID",
    "APP_VERSION_CODE",
    "APP_VERSION_NAME",
    "PACKAGE_NAME",
    "CUSTOM_DATA",
    "STACK_TRACE",
    "STACK_TRACE_HASH",
)

private val acceptedFields = setOf(
    "REPORT_ID",
    "APP_VERSION_CODE",
    "APP_VERSION_NAME",
    "PACKAGE_NAME",
    "PHONE_MODEL",
    "ANDROID_VERSION",
    "BRAND",
    "PRODUCT",
    "TOTAL_MEM_SIZE",
    "AVAILABLE_MEM_SIZE",
    "CUSTOM_DATA",
    "STACK_TRACE",
    "STACK_TRACE_HASH",
    "CRASH_CONFIGURATION",
    "DISPLAY",
    "USER_APP_START_DATE",
    "USER_CRASH_DATE",
    "DUMPSYS_MEMINFO",
    "IS_SILENT",
    "THREAD_DETAILS",
)

private val acceptedCustomDataFields = setOf(
    "context_schema",
    "build_type",
    "feature",
    "previous_exit",
)

private val fingerprintPattern = Regex("[0-9a-fA-F]{8,128}")
private val safeVersionPattern = Regex("[0-9A-Za-z][0-9A-Za-z._+\\-]{0,63}")
private val safeTokenPattern = Regex("[0-9A-Za-z][0-9A-Za-z._\\-]{0,63}")
private val exceptionTypePattern = Regex("[A-Za-z_$][A-Za-z0-9_.$]{0,255}")
private val stackFramePattern = Regex(
    "[A-Za-z0-9_.$/<>\\-]{1,512}\\([A-Za-z0-9_.$ <>\\-]{1,256}(?::[0-9]{1,9})?\\)"
)
private val elidedFramePattern = Regex("\\.\\.\\. [0-9]{1,9} more")
