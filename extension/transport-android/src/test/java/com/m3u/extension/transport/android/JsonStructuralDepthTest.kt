package com.m3u.extension.transport.android

import kotlin.test.Test
import kotlin.test.assertFailsWith

class JsonStructuralDepthTest {
    @Test
    fun `maximum safe nesting is accepted`() {
        nestedArrays(MAX_EXTENSION_JSON_STRUCTURAL_DEPTH)
            .requireSafeExtensionJsonDepth()
    }

    @Test
    fun `excessive nesting is rejected before decoding`() {
        assertFailsWith<IllegalArgumentException> {
            nestedArrays(MAX_EXTENSION_JSON_STRUCTURAL_DEPTH + 1)
                .requireSafeExtensionJsonDepth()
        }
    }

    @Test
    fun `container characters inside strings do not consume depth`() {
        """{"text":"${"[]{}".repeat(1_000)}"}"""
            .requireSafeExtensionJsonDepth(maximumDepth = 1)
    }

    private fun nestedArrays(depth: Int): String =
        "[".repeat(depth) + "0" + "]".repeat(depth)
}
