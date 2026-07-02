package com.m3u.data.service.internal

import java.util.Base64

internal object ClearKeyLicense {
    fun normalize(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank() ||
            trimmed.startsWith("http", ignoreCase = true) ||
            trimmed.contains("\"keys\"")
        ) {
            return value
        }

        val keys = trimmed
            .removeSurrounding("{", "}")
            .split(",")
            .mapNotNull { part ->
                val index = part.indexOf(':')
                if (index <= 0) return@mapNotNull null
                val kid = part.take(index).trim().trim('"')
                val key = part.drop(index + 1).trim().trim('"')
                val encodedKid = kid.hexToBase64UrlOrNull()
                val encodedKey = key.hexToBase64UrlOrNull()
                if (encodedKid == null || encodedKey == null) null else encodedKid to encodedKey
            }
        if (keys.isEmpty()) return value

        val jsonKeys = keys.joinToString(separator = ",") { (kid, key) ->
            """{"kty":"oct","kid":"$kid","k":"$key"}"""
        }
        return """{"keys":[$jsonKeys],"type":"temporary"}"""
    }

    private fun String.hexToBase64UrlOrNull(): String? {
        val clean = trim().removePrefix("0x")
        if (clean.length % 2 != 0 || clean.any { it.digitToIntOrNull(16) == null }) {
            return null
        }
        val bytes = ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
