package com.m3u.smartphone.stability

import java.util.concurrent.atomic.AtomicReference
import org.acra.ACRA

internal enum class StabilityFeature(val wireName: String) {
    APP("app"),
    HOME("home"),
    FAVORITES("favorites"),
    SETTINGS("settings"),
}

internal object StabilityReporter {
    private val buildType = AtomicReference("unknown")
    private val feature = AtomicReference(StabilityFeature.APP)

    fun initialize(buildType: String) {
        this.buildType.set(buildType)
        feature.set(StabilityFeature.APP)
    }

    fun setFeature(feature: StabilityFeature) {
        this.feature.set(feature)
    }

    fun reportPreviousExit(kind: PreviousExitKind) {
        ACRA.errorReporter.handleSilentException(
            when (kind) {
                PreviousExitKind.ANR -> PreviousAnrExit()
                PreviousExitKind.NATIVE_CRASH -> PreviousNativeCrashExit()
            },
        )
    }

    fun snapshot(exception: Throwable?): Map<String, String> = buildMap {
        put("context_schema", "1")
        put("build_type", buildType.get())
        put("feature", feature.get().wireName)
        exception.previousExitKind()?.let { kind -> put("previous_exit", kind.wireName) }
    }
}

internal class PreviousAnrExit : RuntimeException()

internal class PreviousNativeCrashExit : RuntimeException()

private fun Throwable?.previousExitKind(): PreviousExitKind? = when (this) {
    is PreviousAnrExit -> PreviousExitKind.ANR
    is PreviousNativeCrashExit -> PreviousExitKind.NATIVE_CRASH
    else -> null
}
