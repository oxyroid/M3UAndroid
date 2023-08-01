@file:Suppress("unused")

package com.m3u.data.remote.api.di

import com.m3u.core.util.serialization.asConverterFactory
import com.m3u.data.remote.api.RemoteApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.create
import java.net.Proxy
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ApiModule {
    @Provides
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideRetrofitBuilder(
        json: Json,
        proxy: Proxy
    ): Retrofit.Builder {
        val mediaType = "application/json".toMediaType()
        return Retrofit.Builder()
            .client(
                OkHttpClient.Builder()
                    .proxy(proxy)
                    .build()
            )
            .addConverterFactory(json.asConverterFactory(mediaType))
    }

    @Provides
    fun provideRemoteApi(
        builder: Retrofit.Builder
    ): RemoteApi = builder
        .baseUrl("https://api.github.com")
        .build()
        .create()

    @Provides
    fun provideProxy(): Proxy {
        return Proxy.NO_PROXY
    }
}