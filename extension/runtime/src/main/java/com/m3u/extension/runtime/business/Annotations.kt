package com.m3u.extension.runtime.business

interface RemoteModule {
    val module: String
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class RemoteMethod(
    val name: String
)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class RemoteMethodParam
