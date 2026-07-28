package com.m3u.business.setting

import com.m3u.extension.api.ExtensionNetworkOrigin
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingType

enum class ExtensionSettingInputError {
    REQUIRED,
    TOO_LONG,
    INVALID_NUMBER,
    INVALID_BOOLEAN,
    INVALID_CHOICE,
    INVALID_NETWORK_ORIGIN,
}

fun ExtensionSettingField.extensionSettingInputError(
    rawValue: String,
    secretConfigured: Boolean,
): ExtensionSettingInputError? {
    if (rawValue.length > MAX_EXTENSION_SETTING_VALUE_LENGTH) {
        return ExtensionSettingInputError.TOO_LONG
    }
    if (
        type == ExtensionSettingType.SECRET &&
        secretConfigured &&
        rawValue.isEmpty()
    ) {
        return null
    }
    if (required && rawValue.isBlank()) {
        return ExtensionSettingInputError.REQUIRED
    }
    if (!required && rawValue.isEmpty()) {
        return null
    }
    if (networkOrigin) {
        return ExtensionSettingInputError.INVALID_NETWORK_ORIGIN.takeUnless {
            runCatching { ExtensionNetworkOrigin(rawValue) }.isSuccess
        }
    }
    return when (type) {
        ExtensionSettingType.TEXT,
        ExtensionSettingType.SECRET -> null

        ExtensionSettingType.NUMBER -> ExtensionSettingInputError.INVALID_NUMBER.takeUnless {
            rawValue.canonicalExtensionNumberOrNull() != null
        }

        ExtensionSettingType.BOOLEAN -> ExtensionSettingInputError.INVALID_BOOLEAN.takeUnless {
            rawValue.toBooleanStrictOrNull() != null
        }

        ExtensionSettingType.SINGLE_CHOICE ->
            ExtensionSettingInputError.INVALID_CHOICE.takeUnless {
                choices.any { choice -> choice.value == rawValue }
            }
    }
}

fun ExtensionSettingField.normalizedExtensionSettingValue(rawValue: String): String =
    if (type == ExtensionSettingType.NUMBER) {
        rawValue.canonicalExtensionNumberOrNull() ?: rawValue
    } else {
        rawValue
    }

fun String.canonicalExtensionNumberOrNull(): String? {
    val trimmed = trim()
    val normalized = if (
        '.' !in trimmed &&
        trimmed.count { character -> character == ',' } == 1
    ) {
        trimmed.replace(',', '.')
    } else {
        trimmed
    }
    return normalized.takeIf { value ->
        value.toDoubleOrNull()?.isFinite() == true
    }
}

private const val MAX_EXTENSION_SETTING_VALUE_LENGTH = 16_384
