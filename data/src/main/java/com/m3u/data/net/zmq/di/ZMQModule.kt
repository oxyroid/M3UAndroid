package com.m3u.data.net.zmq.di

import com.m3u.data.net.zmq.ZMQClient
import com.m3u.data.net.zmq.ZMQContracts
import com.m3u.data.net.zmq.ZMQServer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ZMQModule {
    @Provides
    @Singleton
    fun provideZMQServer(): ZMQServer = ZMQServer(ZMQContracts.PORT)

    @Provides
    @Singleton
    fun provideZMQClient(): ZMQClient = ZMQClient(ZMQContracts.PORT)
}