package com.m3u.business.setting

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionSettingUpdateGateTest {
    private val gate = ExtensionSettingUpdateGate()

    @Test
    fun `same setting cannot start twice before completion`() {
        assertTrue(gate.tryStart(EXTENSION_ID, SETTING_KEY))
        assertFalse(gate.tryStart(EXTENSION_ID, SETTING_KEY))
    }

    @Test
    fun `completed setting can start again`() {
        assertTrue(gate.tryStart(EXTENSION_ID, SETTING_KEY))

        gate.finish(EXTENSION_ID, SETTING_KEY)

        assertTrue(gate.tryStart(EXTENSION_ID, SETTING_KEY))
    }

    @Test
    fun `different settings remain independent`() {
        assertTrue(gate.tryStart(EXTENSION_ID, SETTING_KEY))
        assertTrue(gate.tryStart(EXTENSION_ID, "playback/quality"))
        assertTrue(gate.tryStart("com.example.other", SETTING_KEY))
    }

    private companion object {
        const val EXTENSION_ID = "com.example.extension"
        const val SETTING_KEY = "playback/api-origin"
    }
}
