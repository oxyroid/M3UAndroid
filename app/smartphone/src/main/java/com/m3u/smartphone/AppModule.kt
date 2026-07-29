@file:Suppress("unused")

package com.m3u.smartphone

import com.m3u.core.foundation.architecture.Publisher
import com.m3u.smartphone.startup.ApplicationStartupTask
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AppModule {
    @Binds
    @Singleton
    fun bindPublisher(provider: AppPublisher): Publisher

    @Multibinds
    fun bindApplicationStartupTasks(): Set<ApplicationStartupTask>
}
