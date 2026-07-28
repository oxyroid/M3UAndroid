package com.m3u.business.setting

import com.m3u.extension.api.ExtensionSettingChoice
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExtensionSettingInputValidationTest {
    @Test
    fun `required and configured secret states are distinguished`() {
        val field = field(type = ExtensionSettingType.SECRET, required = true)

        assertEquals(
            ExtensionSettingInputError.REQUIRED,
            field.extensionSettingInputError(rawValue = "", secretConfigured = false),
        )
        assertNull(
            field.extensionSettingInputError(rawValue = "", secretConfigured = true)
        )
    }

    @Test
    fun `number and choice values follow their declared types`() {
        val number = field(type = ExtensionSettingType.NUMBER)
        val choice = field(
            type = ExtensionSettingType.SINGLE_CHOICE,
            choices = listOf(ExtensionSettingChoice(value = "direct", label = "Direct")),
        )

        assertNull(number.extensionSettingInputError("2.5", secretConfigured = false))
        assertNull(number.extensionSettingInputError("2,5", secretConfigured = false))
        assertEquals(
            "2.5",
            number.normalizedExtensionSettingValue(" 2,5 "),
        )
        assertEquals(
            ExtensionSettingInputError.INVALID_NUMBER,
            number.extensionSettingInputError("two", secretConfigured = false),
        )
        assertNull(choice.extensionSettingInputError("direct", secretConfigured = false))
        assertEquals(
            ExtensionSettingInputError.INVALID_CHOICE,
            choice.extensionSettingInputError("auto", secretConfigured = false),
        )
    }

    @Test
    fun `network origin must be an exact http or https origin`() {
        val field = field(
            type = ExtensionSettingType.TEXT,
            required = true,
            networkOrigin = true,
        )

        assertNull(
            field.extensionSettingInputError(
                rawValue = "https://example.com",
                secretConfigured = false,
            )
        )
        assertEquals(
            ExtensionSettingInputError.INVALID_NETWORK_ORIGIN,
            field.extensionSettingInputError(
                rawValue = "https://example.com/path",
                secretConfigured = false,
            ),
        )
    }

    @Test
    fun `host setting value limit is checked before parsing`() {
        val field = field(type = ExtensionSettingType.TEXT)

        assertEquals(
            ExtensionSettingInputError.TOO_LONG,
            field.extensionSettingInputError(
                rawValue = "a".repeat(16_385),
                secretConfigured = false,
            ),
        )
    }

    private fun field(
        type: ExtensionSettingType,
        required: Boolean = false,
        choices: List<ExtensionSettingChoice> = emptyList(),
        networkOrigin: Boolean = false,
    ) = ExtensionSettingField(
        key = "field",
        label = "Field",
        type = type,
        required = required,
        choices = choices,
        networkOrigin = networkOrigin,
    )
}
