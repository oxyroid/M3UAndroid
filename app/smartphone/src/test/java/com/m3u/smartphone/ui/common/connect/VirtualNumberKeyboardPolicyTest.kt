package com.m3u.smartphone.ui.common.connect

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VirtualNumberKeyboardPolicyTest {
    @Test
    fun `digit keys remain enabled until the sixth digit`() {
        assertTrue(areRemoteDigitKeysEnabled(codeLength = 0))
        assertTrue(areRemoteDigitKeysEnabled(codeLength = 5))
    }

    @Test
    fun `digit keys are disabled at and beyond the six digit limit`() {
        assertFalse(areRemoteDigitKeysEnabled(codeLength = 6))
        assertFalse(areRemoteDigitKeysEnabled(codeLength = 7))
    }
}
