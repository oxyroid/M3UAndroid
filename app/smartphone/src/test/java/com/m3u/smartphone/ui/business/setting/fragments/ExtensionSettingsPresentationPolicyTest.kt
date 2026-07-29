package com.m3u.smartphone.ui.business.setting.fragments

import androidx.compose.ui.text.style.TextDirection
import com.m3u.data.repository.extension.ExtensionNetworkOriginState
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionSettingsPresentationPolicyTest {
    @Test
    fun `explicitly directional setting values use left to right text direction`() {
        val technicalFields = listOf(
            settingField(key = "port", type = ExtensionSettingType.NUMBER),
            settingField(key = "server", networkOrigin = true),
        )

        technicalFields.forEach { field ->
            assertEquals(
                TextDirection.Ltr,
                extensionSettingInputTextDirection(field),
                "Expected ${field.key} to use LTR input",
            )
        }
    }

    @Test
    fun `unconstrained setting values follow their content direction`() {
        listOf(
            "display_name",
            "base_url",
            "postal_address",
            "password",
        ).forEach { key ->
            assertEquals(
                TextDirection.ContentOrLtr,
                extensionSettingInputTextDirection(
                    settingField(
                        key = key,
                        type = if (key == "password") {
                            ExtensionSettingType.SECRET
                        } else {
                            ExtensionSettingType.TEXT
                        },
                    )
                ),
                "Expected $key to follow its content direction",
            )
        }
    }

    @Test
    fun `unchanged network origin can be resubmitted for approval`() {
        assertTrue(
            shouldShowExtensionSettingSaveAction(
                dirty = false,
                networkOriginState = ExtensionNetworkOriginState.REQUIRES_APPROVAL,
            )
        )
        assertFalse(
            shouldShowExtensionSettingSaveAction(
                dirty = false,
                networkOriginState = ExtensionNetworkOriginState.APPROVED,
            )
        )
        assertTrue(
            shouldShowExtensionSettingSaveAction(
                dirty = true,
                networkOriginState = ExtensionNetworkOriginState.NOT_CONFIGURED,
            )
        )
    }

    private fun settingField(
        key: String,
        type: ExtensionSettingType = ExtensionSettingType.TEXT,
        networkOrigin: Boolean = false,
    ): ExtensionSettingField = ExtensionSettingField(
        key = key,
        label = key,
        type = type,
        networkOrigin = networkOrigin,
    )
}
