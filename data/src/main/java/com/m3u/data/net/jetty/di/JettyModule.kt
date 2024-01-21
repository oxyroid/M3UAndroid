package com.m3u.data.net.jetty.di

import com.m3u.data.net.jetty.JettyClient
import com.m3u.data.net.jetty.JettyContracts
import com.m3u.data.net.jetty.JettyServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object JettyModule {
    @Provides
    @Singleton
    fun provideJettyServer(): JettyServer = JettyServer(JettyContracts.PORT)

    @Provides
    @Singleton
    fun provideJettyClient(): JettyClient = JettyClient(JettyContracts.PORT)
}