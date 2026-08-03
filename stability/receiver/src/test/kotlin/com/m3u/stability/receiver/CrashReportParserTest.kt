package com.m3u.stability.receiver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CrashReportParserTest {
    @Test
    fun `accepts the schema one allowlist and strips exception messages again`() {
        val result = CrashReportParser.parse(
            validReport(
                stackTrace = """
                    java.lang.IllegalStateException: password=server-side-defense
                    at com.m3u.Feature.run(Feature.kt:42)
                    at https://private.example/token
                    Caused by: java.lang.RuntimeException: https://private.example/token
                    at com.m3u.Other.run(Other.kt:7)
                """.trimIndent(),
            ).encodeToByteArray(),
        )

        val report = assertIs<ReportParseResult.Accepted>(result).report
        assertEquals("java.lang.IllegalStateException", report.exceptionType)
        assertTrue(report.stackTrace.contains("Feature.kt:42"))
        assertTrue(report.stackTrace.contains("Caused by: java.lang.RuntimeException"))
        assertFalse(report.stackTrace.contains("password"))
        assertFalse(report.stackTrace.contains("private.example"))
        assertEquals("app", report.feature)
    }

    @Test
    fun `rejects forbidden report and custom data fields`() {
        assertIs<ReportParseResult.Rejected>(
            CrashReportParser.parse(
                validReport(extraReportField = "\"LOGCAT\":\"secret\",").encodeToByteArray(),
            )
        )
        assertIs<ReportParseResult.Rejected>(
            CrashReportParser.parse(
                validReport(customData = "\"feature\":\"app\",\"provider_url\":\"secret\"")
                    .encodeToByteArray(),
            )
        )
    }

    @Test
    fun `rejects another package and oversized stack trace`() {
        assertIs<ReportParseResult.Rejected>(
            CrashReportParser.parse(
                validReport(packageName = "example.other.app").encodeToByteArray(),
            )
        )
        assertIs<ReportParseResult.Rejected>(
            CrashReportParser.parse(
                validReport(stackTrace = "x".repeat(128 * 1024 + 1)).encodeToByteArray(),
            )
        )
    }
}

internal fun validReport(
    versionCode: Long = 145L,
    versionName: String = "1.15.1",
    fingerprint: String = "332bfa24",
    packageName: String = REPORT_PACKAGE,
    stackTrace: String = "java.lang.IllegalStateException\n\tat Probe.run(Probe.kt:1)",
    customData: String = "\"context_schema\":\"1\",\"build_type\":\"debug\",\"feature\":\"app\"",
    extraReportField: String = "",
): String =
    """{"REPORT_ID":"1a9ccceb-fdf4-4c54-8b37-4793fa69b01f","APP_VERSION_CODE":$versionCode,"APP_VERSION_NAME":"$versionName","PACKAGE_NAME":"$packageName",$extraReportField"PHONE_MODEL":"Pixel 6 Pro","ANDROID_VERSION":"16","CUSTOM_DATA":{$customData},"STACK_TRACE":${stackTrace.jsonString()},"STACK_TRACE_HASH":"$fingerprint","USER_CRASH_DATE":"2026-08-03T10:00:00Z","IS_SILENT":false}"""

private fun String.jsonString(): String = buildString(length + 2) {
    append('"')
    this@jsonString.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}
