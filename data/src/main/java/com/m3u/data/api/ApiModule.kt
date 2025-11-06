@file:Suppress("unused")

package com.m3u.data.api

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
import timber.log.Timber
import java.util.concurrent.TimeUnit
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
    fun provideOkhttpClient(): OkHttpClient {
        val timber = Timber.tag("OkHttpClient")

        return OkHttpClient.Builder()
            .authenticator(Authenticator.JAVA_NET_AUTHENTICATOR)
            // ========================================
            // TIMEOUT CONFIGURATION FOR LARGE FILES
            // ========================================
            // Based on OkHttp best practices for streaming large files (40MB+ M3U playlists)
            // - connectTimeout: Time to establish TCP connection (30s for slow networks)
            // - readTimeout: Time between each data chunk (90s for slow servers/networks)
            // - writeTimeout: Time to send request data (30s sufficient for GET requests)
            // - callTimeout: Total time for entire call (5 minutes for large downloads)
            //
            // Key insight: With streaming, readTimeout resets with each received chunk,
            // so even large files work as long as data flows within 90s intervals.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)      // Critical for slow M3U servers
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)       // Total max time for large downloads
            // ========================================
            // LOGGING AND ERROR HANDLING INTERCEPTOR
            // ========================================
            .addInterceptor { chain ->
                val request = chain.request()
                val url = request.url.toString()

                timber.d("→ HTTP ${request.method} ${url.take(100)}")
                val startTime = System.currentTimeMillis()

                try {
                    val response = chain.proceed(request)
                    val duration = System.currentTimeMillis() - startTime

                    timber.d("← ${response.code} ${url.take(100)} (${duration}ms)")

                    // Log response body size for debugging
                    response.body?.contentLength()?.let { size ->
                        timber.d("  Response size: ${size / 1024}KB")
                    }

                    response
                } catch (e: java.net.SocketTimeoutException) {
                    // CRITICAL: Socket timeout - log detailed info for debugging
                    val duration = System.currentTimeMillis() - startTime
                    timber.e(e, "✗ TIMEOUT after ${duration}ms for ${url.take(100)}")
                    timber.e("  This usually means:")
                    timber.e("  - Server took too long to respond (>90s between chunks)")
                    timber.e("  - Network is very slow")
                    timber.e("  - Server is overloaded")

                    // Return error response with detailed timeout information
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(408)  // Request Timeout (proper HTTP status)
                        .message("Request Timeout after ${duration}ms")
                        .body("{\"error\":\"SocketTimeoutException\",\"message\":\"${e.message}\",\"duration_ms\":$duration}".toResponseBody())
                        .build()
                } catch (e: java.io.IOException) {
                    // Network I/O error (connection failed, host unreachable, etc.)
                    val duration = System.currentTimeMillis() - startTime
                    timber.e(e, "✗ NETWORK ERROR after ${duration}ms for ${url.take(100)}")

                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(503)  // Service Unavailable
                        .message("Network I/O Error: ${e.message}")
                        .body("{\"error\":\"IOException\",\"message\":\"${e.message}\",\"duration_ms\":$duration}".toResponseBody())
                        .build()
                } catch (e: Exception) {
                    // Catch-all for unexpected errors
                    val duration = System.currentTimeMillis() - startTime
                    timber.e(e, "✗ UNEXPECTED ERROR after ${duration}ms for ${url.take(100)}")

                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(500)  // Internal Server Error
                        .message("Unexpected Error: ${e.message}")
                        .body("{\"error\":\"${e.javaClass.simpleName}\",\"message\":\"${e.message}\",\"duration_ms\":$duration}".toResponseBody())
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
}
