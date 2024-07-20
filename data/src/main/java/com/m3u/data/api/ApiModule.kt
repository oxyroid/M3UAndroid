@file:Suppress("unused")

package com.m3u.data.api

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.m3u.core.architecture.logger.Logger
import com.m3u.data.Certs
import com.m3u.data.SSLs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Retrofit
import retrofit2.create
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class OkhttpClient(val chucker: Boolean)

@Module
@InstallIn(SingletonComponent::class)
internal object ApiModule {
    @Provides
    @Singleton
    @OkhttpClient(true)
    fun provideChuckerOkhttpClient(
        @ApplicationContext context: Context,
        @OkhttpClient(false) okhttpClient: OkHttpClient
    ): OkHttpClient {
        return okhttpClient
            .newBuilder()
            .addInterceptor(
                ChuckerInterceptor.Builder(context)
                    .maxContentLength(10240)
                    .build()
            )
            .sslSocketFactory(SSLs.TLSTrustAll.socketFactory, Certs.TrustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @Provides
    @Singleton
    @OkhttpClient(false)
    fun provideOkhttpClient(
        logger: Logger
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .authenticator(Authenticator.JAVA_NET_AUTHENTICATOR)
            .addInterceptor { chain ->
                val request = chain.request()
                try {
                    chain.proceed(request).apply {
                        val body = body
                        if (body != null) {
                            val contentType = body.contentType()
                            val isWebPage = contentType != null &&
                                    contentType.type == "text" &&
                                    contentType.subtype == "html"
                            if (isWebPage) {
                                WebPageManager.push(body)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.log(e)
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(999)
                        .message(e.message.orEmpty())
                        .body("{${e}}".toResponseBody())
                        .build()
                }
            }
            .sslSocketFactory(SSLs.TLSTrustAll.socketFactory, Certs.TrustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofitBuilder(
        @OkhttpClient(true) okHttpClient: OkHttpClient
    ): Retrofit.Builder {
        val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }
        val mediaType = "application/json".toMediaType()
        return Retrofit.Builder()
            .addConverterFactory(json.asConverterFactory(mediaType))
            .client(okHttpClient)
    }

    @Provides
    fun provideGithubApi(
        builder: Retrofit.Builder
    ): GithubApi = builder
        .baseUrl(BaseUrls.GITHUB_BASE_URL)
        .build()
        .create()
}
