package com.m3u.core.architecture

import kotlinx.serialization.Serializable

interface Publisher {
    val repository: String get() = "M3UAndroid"
    val applicationId: String
    val versionName: String
    val versionCode: Int
    val debug: Boolean
    val snapshot: Boolean
    val lite: Boolean
    val model: String
    val abi: Abi
    val tv: Boolean
}

@JvmInline
@Serializable
value class Abi private constructor(
    val value: String
) {
    fun accept(self: Abi, target: Abi): Boolean {
        check(self != unsupported) { "self abi cannot be unsupported!" }
        if (target == unsupported) return false
        if (self == target) return true
        return self == universal
    }

    companion object {
        val universal = Abi("universal")
        val x86 = Abi("x86")
        val x86_64 = Abi("x86_64")
        val arm64_v8a = Abi("arm64-v8a")
        val arm64_v7a = Abi("arm64-v7a")
        val unsupported = Abi("unsupported")

        fun of(value: String): Abi {
            return when (value) {
                x86.value -> x86
                x86_64.value -> x86_64
                arm64_v7a.value -> arm64_v7a
                arm64_v8a.value -> arm64_v8a
                universal.value -> universal
                else -> unsupported
            }
        }
    }
}
