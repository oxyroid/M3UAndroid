package com.m3u.smartphone.startup

import androidx.work.WorkManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Inject

internal class DebugDefaultLibraryStartupTask @Inject constructor() :
    ApplicationStartupTask {
    override fun enqueue(workManager: WorkManager) {
        DebugDefaultLibraryWorker.enqueue(workManager)
    }
}

internal class DebugEmbyAccountStartupTask @Inject constructor() :
    ApplicationStartupTask {
    override fun enqueue(workManager: WorkManager) {
        if (DebugEmbyAccountConfiguration.configuredOrNull() != null) {
            DebugEmbyAccountWorker.enqueue(workManager)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface DebugDefaultLibraryStartupModule {
    @Binds
    @IntoSet
    fun bindDebugDefaultLibraryStartupTask(
        task: DebugDefaultLibraryStartupTask,
    ): ApplicationStartupTask

    @Binds
    @IntoSet
    fun bindDebugEmbyAccountStartupTask(
        task: DebugEmbyAccountStartupTask,
    ): ApplicationStartupTask
}
