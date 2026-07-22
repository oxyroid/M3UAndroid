package com.m3u.extension.transport.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CertificateSetFingerprintTest {
    @Test
    fun `single certificate keeps the existing sha256 format`() {
        val fingerprint = certificateSetSha256(listOf("abc".encodeToByteArray()))

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223" +
                "b00361a396177a9cb410ff61f20015ad",
            fingerprint,
        )
    }

    @Test
    fun `multiple certificate fingerprints are deduplicated and sorted`() {
        val fingerprint = certificateSetSha256(
            listOf(
                byteArrayOf(),
                "abc".encodeToByteArray(),
                byteArrayOf(),
            ),
        )

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223" +
                "b00361a396177a9cb410ff61f20015ad," +
                "e3b0c44298fc1c149afbf4c8996fb924" +
                "27ae41e4649b934ca495991b7852b855",
            fingerprint,
        )
    }

    @Test
    fun `certificate order does not change the fingerprint`() {
        val first = "first".encodeToByteArray()
        val second = "second".encodeToByteArray()

        assertEquals(
            certificateSetSha256(listOf(first, second)),
            certificateSetSha256(listOf(second, first)),
        )
    }

    @Test
    fun `empty certificate set is rejected`() {
        assertFailsWith<IllegalStateException> {
            certificateSetSha256(emptyList())
        }
    }
}
