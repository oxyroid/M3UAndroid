package com.m3u.smartphone.stability

import org.acra.ReportField

internal val safeCrashReportFields = listOf(
    ReportField.REPORT_ID,
    ReportField.APP_VERSION_CODE,
    ReportField.APP_VERSION_NAME,
    ReportField.PACKAGE_NAME,
    ReportField.PHONE_MODEL,
    ReportField.ANDROID_VERSION,
    ReportField.BRAND,
    ReportField.PRODUCT,
    ReportField.TOTAL_MEM_SIZE,
    ReportField.AVAILABLE_MEM_SIZE,
    ReportField.CUSTOM_DATA,
    ReportField.STACK_TRACE,
    ReportField.STACK_TRACE_HASH,
    ReportField.CRASH_CONFIGURATION,
    ReportField.DISPLAY,
    ReportField.USER_APP_START_DATE,
    ReportField.USER_CRASH_DATE,
    ReportField.DUMPSYS_MEMINFO,
    ReportField.IS_SILENT,
    ReportField.THREAD_DETAILS,
)

internal val sensitiveCrashReportFields = setOf(
    ReportField.BUILD_CONFIG,
    ReportField.FILE_PATH,
    ReportField.LOGCAT,
    ReportField.EVENTSLOG,
    ReportField.RADIOLOG,
    ReportField.DROPBOX,
    ReportField.INSTALLATION_ID,
    ReportField.DEVICE_ID,
    ReportField.USER_EMAIL,
    ReportField.USER_COMMENT,
    ReportField.SHARED_PREFERENCES,
    ReportField.APPLICATION_LOG,
    ReportField.ENVIRONMENT,
    ReportField.SETTINGS_SYSTEM,
    ReportField.SETTINGS_SECURE,
    ReportField.SETTINGS_GLOBAL,
    ReportField.USER_IP,
)
