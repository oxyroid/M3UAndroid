package com.m3u.business.setting

data class CodecPackState(
    val enabled: Boolean = false,
    val packId: String = "",
    val abi: String? = null,
    val installed: Boolean = false,
    val installing: Boolean = false,
    val error: String? = null
)