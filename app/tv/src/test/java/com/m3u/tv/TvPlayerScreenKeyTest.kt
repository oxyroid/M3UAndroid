package com.m3u.tv

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvPlayerScreenKeyTest {
    @Test
    fun channelNavigationKeysMapToPreviousAndNextChannels() {
        listOf(
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerChannelNavigationAction.Previous,
                tvPlayerChannelNavigationAction(
                    keyCode = keyCode,
                    isKeyDown = true,
                    repeatCount = 0
                )
            )
        }

        listOf(
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT
        ).forEach { keyCode ->
            assertEquals(
                TvPlayerChannelNavigationAction.Next,
                tvPlayerChannelNavigationAction(
                    keyCode = keyCode,
                    isKeyDown = true,
                    repeatCount = 0
                )
            )
        }
    }

    @Test
    fun channelNavigationIgnoresKeyUpRepeatAndUnmappedKeys() {
        assertNull(
            tvPlayerChannelNavigationAction(
                keyCode = KeyEvent.KEYCODE_CHANNEL_UP,
                isKeyDown = false,
                repeatCount = 0
            )
        )
        assertNull(
            tvPlayerChannelNavigationAction(
                keyCode = KeyEvent.KEYCODE_CHANNEL_UP,
                isKeyDown = true,
                repeatCount = 1
            )
        )
        assertNull(
            tvPlayerChannelNavigationAction(
                keyCode = KeyEvent.KEYCODE_DPAD_CENTER,
                isKeyDown = true,
                repeatCount = 0
            )
        )
    }
}
