@file:Suppress("unused")

package com.m3u.data.di

import com.m3u.core.architecture.FilePathCacher
import com.m3u.data.io.CrashFilePathCacher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface CacherModule {
    @Binds
    @Singleton
    fun bindFilePathCacher(cacher: CrashFilePathCacher): FilePathCacher
}
