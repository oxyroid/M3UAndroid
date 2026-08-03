package com.m3u.smartphone.stability

import android.content.Context
import org.acra.ReportField
import org.acra.builder.ReportBuilder
import org.acra.collector.Collector
import org.acra.config.CoreConfiguration
import org.acra.data.CrashReportData
import org.json.JSONObject

/** Writes a thread-safe, report-local stability context without ACRA's mutable global data map. */
class StabilityContextCollector : Collector {
    override val order: Collector.Order = Collector.Order.LAST

    override fun collect(
        context: Context,
        config: CoreConfiguration,
        reportBuilder: ReportBuilder,
        crashReportData: CrashReportData,
    ) {
        crashReportData.put(
            ReportField.CUSTOM_DATA,
            JSONObject(StabilityReporter.snapshot(reportBuilder.exception)),
        )
    }

    override fun enabled(config: CoreConfiguration): Boolean =
        ReportField.CUSTOM_DATA in config.reportContent
}
