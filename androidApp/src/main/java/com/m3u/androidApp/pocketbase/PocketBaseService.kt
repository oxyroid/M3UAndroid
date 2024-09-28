package com.m3u.androidApp.pocketbase


import com.m3u.androidApp.pocketbase.models.DeviceCreateRequest
import com.m3u.androidApp.pocketbase.models.DeviceResponse
import com.m3u.androidApp.pocketbase.models.SettingsResponse

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Body

interface PocketBaseService {

    @GET("api/collections/settings/records")
    fun getSettings(): Call<SettingsResponse>

    @GET("api/collections/devices/records")
    fun getDeviceByAndroidId(@Query("filter") filter: String): Call<DeviceResponse>

    @POST("api/collections/devices/records")
    fun createDevice(@Body newDevice: DeviceCreateRequest): Call<DeviceResponse>
}
