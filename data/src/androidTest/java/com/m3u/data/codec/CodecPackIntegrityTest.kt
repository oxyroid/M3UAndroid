package com.m3u.data.codec

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecPackIntegrityTest {
    @Test
    fun acceptsExpectedBytesAndRejectsChangedBytes() {
        val expectedSha256 = "ba7816bf8f01cfea414140de5dae2223" +
            "b00361a396177a9cb410ff61f20015ad"

        assertTrue(CodecPackIntegrity.matchesSha256("abc".encodeToByteArray(), expectedSha256))
        assertFalse(CodecPackIntegrity.matchesSha256("abd".encodeToByteArray(), expectedSha256))
    }

    @Test
    fun acceptsExpectedFileAndRejectsChangedFile() {
        val file = File.createTempFile("codec-pack-integrity", ".bin")
        try {
            file.writeText("abc")
            val expectedSha256 = "ba7816bf8f01cfea414140de5dae2223" +
                "b00361a396177a9cb410ff61f20015ad"

            assertTrue(CodecPackIntegrity.matchesSha256(file, expectedSha256))
            file.writeText("changed")
            assertFalse(CodecPackIntegrity.matchesSha256(file, expectedSha256))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsMalformedExpectedDigest() {
        assertThrows(IllegalArgumentException::class.java) {
            CodecPackIntegrity.matchesSha256("abc".encodeToByteArray(), "not-a-sha256")
        }
    }
}
