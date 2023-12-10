package com.m3u.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startUpWithBaseline() = startUp(CompilationMode.Partial())

    @Test
    fun startUpWithoutBaseline() = startUp(CompilationMode.None())

    private fun startUp(mode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = "com.m3u.androidApp",
        metrics = listOf(StartupTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.COLD,
        compilationMode = mode
    ) {
        val width = device.displayWidth
        val height = device.displayHeight
        pressHome()
        startActivityAndWait()
        device.swipe(width / 3 * 2, height / 2, width / 3, height / 2, 50)
        Thread.sleep(2000)
        device.swipe(width / 3 * 2, height / 2, width / 3, height / 2, 50)
        Thread.sleep(2000)
        device.swipe(width / 2, height / 3 * 2, width / 2, height / 3, 50)
        Thread.sleep(2000)
        device.swipe(width / 2, height / 3, width / 2, height / 3 * 2, 50)
    }
}
