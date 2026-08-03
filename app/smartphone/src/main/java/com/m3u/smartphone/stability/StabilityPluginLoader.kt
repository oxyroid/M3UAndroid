package com.m3u.smartphone.stability

import org.acra.collector.ConfigurationCollector
import org.acra.collector.DisplayManagerCollector
import org.acra.collector.MemoryInfoCollector
import org.acra.collector.PackageManagerCollector
import org.acra.collector.SimpleValuesCollector
import org.acra.collector.StacktraceCollector
import org.acra.collector.ThreadCollector
import org.acra.collector.TimeCollector
import org.acra.config.CoreConfiguration
import org.acra.config.LimitingReportAdministrator
import org.acra.interaction.NotificationInteraction
import org.acra.plugins.Plugin
import org.acra.plugins.PluginLoader
import org.acra.plugins.SimplePluginLoader
import org.acra.sender.EmailIntentSenderFactory
import org.acra.sender.HttpSenderFactory
import org.acra.startup.LimiterStartupProcessor
import org.acra.startup.UnapprovedStartupProcessor

/**
 * ACRA normally discovers components from META-INF services. The app excludes META-INF resources,
 * so crash reporting uses an explicit, reviewable component list instead.
 */
internal class StabilityPluginLoader : PluginLoader {
    private val delegate = SimplePluginLoader(
        ConfigurationCollector::class.java,
        DisplayManagerCollector::class.java,
        MemoryInfoCollector::class.java,
        PackageManagerCollector::class.java,
        SimpleValuesCollector::class.java,
        StacktraceCollector::class.java,
        ThreadCollector::class.java,
        TimeCollector::class.java,
        SanitizedStacktraceCollector::class.java,
        StabilityContextCollector::class.java,
        LimitingReportAdministrator::class.java,
        NotificationInteraction::class.java,
        EmailIntentSenderFactory::class.java,
        HttpSenderFactory::class.java,
        LimiterStartupProcessor::class.java,
        UnapprovedStartupProcessor::class.java,
    )

    override fun <T : Plugin> load(clazz: Class<T>): List<T> = delegate.load(clazz)

    override fun <T : Plugin> loadEnabled(
        config: CoreConfiguration,
        clazz: Class<T>,
    ): List<T> = delegate.loadEnabled(config, clazz)
}
