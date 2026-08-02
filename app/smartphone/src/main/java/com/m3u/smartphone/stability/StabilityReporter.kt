package com.m3u.smartphone.stability

import org.acra.ACRA

internal enum class StabilityFeature(val wireName: String) {
    APP("app"),
    HOME("home"),
    FAVORITES("favorites"),
    SETTINGS("settings"),
}

internal object StabilityReporter {
    fun initialize(buildType: String) {
        ACRA.errorReporter.putCustomData("context_schema", "1")
        ACRA.errorReporter.putCustomData("build_type", buildType)
        setFeature(StabilityFeature.APP)
    }

    fun setFeature(feature: StabilityFeature) {
        ACRA.errorReporter.putCustomData("feature", feature.wireName)
    }

    fun reportPreviousExit(kind: PreviousExitKind) {
        ACRA.errorReporter.putCustomData("previous_exit", kind.wireName)
        ACRA.errorReporter.handleSilentException(
            when (kind) {
                PreviousExitKind.ANR -> PreviousAnrExit()
                PreviousExitKind.NATIVE_CRASH -> PreviousNativeCrashExit()
            },
        )
        ACRA.errorReporter.removeCustomData("previous_exit")
    }
}

private class PreviousAnrExit : RuntimeException()

private class PreviousNativeCrashExit : RuntimeException()
