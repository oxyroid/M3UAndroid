package com.m3u.androidApp.koin

import com.m3u.androidApp.AppPublisher
import com.m3u.androidApp.ui.RootViewModel
import com.m3u.core.architecture.Publisher
import com.m3u.features.crash.CrashHandler
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

val AppModule = module {
    singleOf(::AppPublisher) bind Publisher::class
    singleOf(::CrashHandler)
    viewModelOf(::RootViewModel)
}