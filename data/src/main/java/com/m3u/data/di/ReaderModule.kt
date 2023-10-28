@file:Suppress("unused")

package com.m3u.data.di

import com.m3u.core.architecture.FileReader
import com.m3u.data.reader.LogFileReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface ReaderModule {
    @Binds
    @Singleton
    fun bindFileReader(reader: LogFileReader): FileReader
}
