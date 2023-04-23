@file:Suppress("unused")

package com.m3u.data.remote.api.di

import com.m3u.core.util.serialization.asConverterFactory
import com.m3u.data.remote.api.RemoteApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.create

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
        json: Json
    ): Retrofit.Builder {
        val mediaType = "application/json".toMediaType()
        return Retrofit.Builder()
            .addConverterFactory(json.asConverterFactory(mediaType))
    }

    @Provides
    fun provideRemoteApi(
        builder: Retrofit.Builder
    ): RemoteApi = builder
        .baseUrl("https://api.github.com")
        .build()
        .create()
}