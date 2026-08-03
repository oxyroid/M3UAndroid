package com.m3u.smartphone.stability

import android.app.ApplicationExitInfo
import org.acra.ReportField
import org.acra.collector.Collector
import org.acra.config.CoreConfigurationBuilder
import org.acra.config.MailSenderConfiguration
import org.acra.config.getPluginConfiguration
import org.acra.interaction.NotificationInteraction
import org.acra.interaction.ReportInteraction
import org.acra.sender.EmailIntentSenderFactory
import org.acra.sender.HttpSenderFactory
import org.acra.sender.ReportSenderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportPolicyTest {
    @Test
    fun reportFieldsExcludeSensitiveSources() {
        assertTrue(safeCrashReportFields.intersect(sensitiveCrashReportFields).isEmpty())
        assertFalse(ReportField.BUILD_CONFIG in safeCrashReportFields)
        assertFalse(ReportField.LOGCAT in safeCrashReportFields)
        assertFalse(ReportField.SHARED_PREFERENCES in safeCrashReportFields)
    }

    @Test
    fun stackTraceOmitsMessagesFromCausesAndSuppressedExceptions() {
        val cause = IllegalStateException("https://private.example/token?access_token=secret")
        val failure = RuntimeException("password=secret", cause).apply {
            addSuppressed(IllegalArgumentException("Bearer secret-token"))
        }

        val result = SafeStackTraceFormatter.format(failure)

        assertTrue(result.contains(RuntimeException::class.java.name))
        assertTrue(result.contains(IllegalStateException::class.java.name))
        assertTrue(result.contains(IllegalArgumentException::class.java.name))
        assertFalse(result.contains("private.example"))
        assertFalse(result.contains("secret"))
        assertFalse(result.contains("password"))
        assertFalse(result.contains("Bearer"))
    }

    @Test
    fun explicitPluginLoaderIncludesTheFinalSanitizingCollector() {
        val collectors = StabilityPluginLoader().load(Collector::class.java)

        assertTrue(collectors.any { it is SanitizedStacktraceCollector })
        assertTrue(collectors.any { it.order == Collector.Order.FIRST })
        assertTrue(collectors.any { it.order == Collector.Order.LAST })
    }

    @Test
    fun configuredEndpointUsesOnlyAutomaticHttpSender() {
        val config = CoreConfigurationBuilder().apply {
            configureCrashReporting(
                endpoint = "https://crash.example.test/reports",
                fallbackCopy = fallbackCopy(),
            )
        }.build()

        val senders = config.pluginLoader.loadEnabled(config, ReportSenderFactory::class.java)
        val interactions = config.pluginLoader.loadEnabled(config, ReportInteraction::class.java)

        assertTrue(senders.single() is HttpSenderFactory)
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun missingEndpointKeepsUserApprovedMailFallback() {
        val config = CoreConfigurationBuilder().apply {
            configureCrashReporting(endpoint = "", fallbackCopy = fallbackCopy())
        }.build()

        val senders = config.pluginLoader.loadEnabled(config, ReportSenderFactory::class.java)
        val interactions = config.pluginLoader.loadEnabled(config, ReportInteraction::class.java)
        val mailConfig = config.getPluginConfiguration<MailSenderConfiguration>()

        assertTrue(senders.single() is EmailIntentSenderFactory)
        assertTrue(interactions.single() is NotificationInteraction)
        assertTrue(config.sendReportsInDevMode)
        assertEquals(CRASH_REPORT_MAILBOX, mailConfig.mailTo)
    }

    @Test
    fun processExitClassifierOnlyReportsAnrAndNativeCrashes() {
        assertTrue(
            PreviousExitKind.fromReason(ApplicationExitInfo.REASON_ANR) == PreviousExitKind.ANR,
        )
        assertTrue(
            PreviousExitKind.fromReason(ApplicationExitInfo.REASON_CRASH_NATIVE) ==
                PreviousExitKind.NATIVE_CRASH,
        )
        assertTrue(PreviousExitKind.fromReason(ApplicationExitInfo.REASON_CRASH) == null)
        assertTrue(PreviousExitKind.fromReason(ApplicationExitInfo.REASON_LOW_MEMORY) == null)
        assertTrue(PreviousExitKind.fromReason(ApplicationExitInfo.REASON_USER_REQUESTED) == null)
    }

    private fun fallbackCopy() = CrashFallbackCopy(
        notificationTitle = "Crash",
        notificationText = "Share report",
        notificationChannelName = "Crashes",
        mailTo = CRASH_REPORT_MAILBOX,
    )
}
