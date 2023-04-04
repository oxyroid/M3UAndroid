@file:Suppress("unused")

package com.m3u.data.remote.api.di

import com.m3u.core.util.serialization.asConverterFactory
import com.m3u.data.remote.api.GithubApiWrapper
import com.m3u.data.remote.api.GithubRepositoryApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit

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
    fun provideDistributionApi(
        builder: Retrofit.Builder
    ): GithubRepositoryApi = GithubApiWrapper(builder).api
}