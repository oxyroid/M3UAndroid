package com.m3u.smartphone.stability

import android.content.Context
import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.collector.Collector
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData

/** Replaces ACRA's raw stack trace after collection so exception messages never leave the app. */
class SanitizedStacktraceCollector : Collector {
    override val order: Collector.Order = Collector.Order.LAST

    override fun collect(
        context: Context,
        config: CoreConfiguration,
        reportBuilder: ReportBuilder,
        crashReportData: CrashReportData,
    ) {
        crashReportData.put(
            ReportField.STACK_TRACE,
            SafeStackTraceFormatter.format(reportBuilder.exception),
        )
    }

    override fun enabled(config: CoreConfiguration): Boolean =
        ReportField.STACK_TRACE in config.reportContent
}
