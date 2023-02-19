package com.m3u.core.util.serialization

import kotlinx.serialization.DeserializationStrategy
import okhttp3.ResponseBody
import retrofit2.Converter

internal class DeserializationStrategyConverter<T>(
    private val loader: DeserializationStrategy<T>,
    private val serializer: Serializer
) : Converter<ResponseBody, T> {
    override fun convert(value: ResponseBody) = serializer.fromResponseBody(loader, value)
}
