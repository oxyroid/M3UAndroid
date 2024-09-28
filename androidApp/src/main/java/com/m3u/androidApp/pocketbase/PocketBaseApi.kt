package com.m3u.androidApp.pocketbase


import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object PocketBaseApi {
    private const val BASE_URL = "https://ipxtv.tijorat.net/"

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val service: PocketBaseService = retrofit.create(PocketBaseService::class.java)
}
