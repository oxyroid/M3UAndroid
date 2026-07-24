package com.m3u.data.codec

import java.io.File
import java.security.MessageDigest

internal object CodecPackIntegrity {
    fun matchesSha256(bytes: ByteArray, expectedSha256: String): Boolean {
        return MessageDigest.isEqual(
            sha256(bytes),
            expectedSha256.hexToByteArray()
        )
    }

    fun matchesSha256(file: File, expectedSha256: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return MessageDigest.isEqual(
            digest.digest(),
            expectedSha256.hexToByteArray()
        )
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun String.hexToByteArray(): ByteArray {
        require(length == SHA_256_HEX_LENGTH && all { character -> character.isHexDigit() }) {
            "Expected a lowercase or uppercase SHA-256 digest."
        }
        return chunked(2)
            .map { octet -> octet.toInt(radix = 16).toByte() }
            .toByteArray()
    }

    private fun Char.isHexDigit(): Boolean {
        return this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }

    private const val SHA_256_HEX_LENGTH = 64
}
