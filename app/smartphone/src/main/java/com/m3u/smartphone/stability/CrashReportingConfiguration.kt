package com.m3u.smartphone.stability

import org.acra.config.CoreConfigurationBuilder
import org.acra.config.httpSender
import org.acra.config.limiter
import org.acra.config.mailSender
import org.acra.config.notification
import org.acra.data.StringFormat
import org.acra.sender.HttpSender
import java.util.concurrent.TimeUnit

internal data class CrashFallbackCopy(
    val notificationTitle: String,
    val notificationText: String,
    val notificationChannelName: String,
    val mailTo: String,
)

internal fun CoreConfigurationBuilder.configureCrashReporting(
    endpoint: String,
    fallbackCopy: CrashFallbackCopy,
) {
    reportFormat = StringFormat.JSON
    reportContent = safeCrashReportFields
    pluginLoader = StabilityPluginLoader()
    sendReportsInDevMode = endpoint.isNotBlank()

    if (endpoint.isNotBlank()) {
        httpSender {
            uri = endpoint
            httpMethod = HttpSender.Method.POST
            connectionTimeout = 5_000
            socketTimeout = 15_000
            compress = true
            httpHeaders = mapOf("X-M3U-Report-Schema" to "1")
        }
    } else {
        notification {
            title = fallbackCopy.notificationTitle
            text = fallbackCopy.notificationText
            channelName = fallbackCopy.notificationChannelName
        }
        mailSender {
            mailTo = fallbackCopy.mailTo
            reportAsFile = true
            reportFileName = "Crash.json"
        }
    }

    limiter {
        periodUnit = TimeUnit.DAYS
        period = 7
        overallLimit = 25
        stacktraceLimit = 3
        exceptionClassLimit = 10
        failedReportLimit = 5
        deleteReportsOnAppUpdate = true
        resetLimitsOnAppUpdate = true
    }
}
