package com.m3u.extension.api.client

import com.m3u.extension.api.model.GetAppInfoResponse

@Module("info")
interface InfoApi {
    @Method("getAppInfo")
    suspend fun getAppInfo(): GetAppInfoResponse
}