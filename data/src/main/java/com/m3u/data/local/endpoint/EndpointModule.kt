package com.m3u.data.local.endpoint

import com.m3u.core.architecture.Publisher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EndpointModule {
    @Provides
    @Singleton
    fun provideSayHelloEndpoint(publisher: Publisher): Endpoint.SayHello =
        Endpoint.SayHello(publisher)
}