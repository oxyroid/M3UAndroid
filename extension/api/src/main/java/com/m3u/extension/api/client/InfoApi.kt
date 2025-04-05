package com.m3u.extension.api.client

import androidx.annotation.Keep
import com.m3u.extension.api.model.GetAppInfoResponse

@Keep
@Module("info")
interface InfoApi {
    @Method("getAppInfo")
    suspend fun getAppInfo(): GetAppInfoResponse
}