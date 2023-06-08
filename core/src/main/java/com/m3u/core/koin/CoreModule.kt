package com.m3u.core.koin

import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.configuration.SharedConfiguration
import com.m3u.core.architecture.logger.AndroidLogger
import com.m3u.core.architecture.logger.BannerLogger
import com.m3u.core.architecture.logger.FileLogger
import com.m3u.core.architecture.logger.Logger
import com.m3u.core.architecture.reader.FileReader
import com.m3u.core.architecture.reader.LogFileReader
import org.koin.core.module.dsl.named
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.bind
import org.koin.dsl.module

object CoreModule {
    val AndroidPlatform = module {
        singleOf(::SharedConfiguration) bind Configuration::class
        singleOf(::AndroidLogger) bind Logger::class withOptions {
            named("android")
        }
        singleOf(::FileLogger) bind Logger::class withOptions {
            named("file")
        }
        singleOf(::LogFileReader) bind FileReader::class
    }
    val SharedPlatform = module {
        singleOf(::BannerLogger) bind Logger::class withOptions {
            named("banner")
        }
    }
}