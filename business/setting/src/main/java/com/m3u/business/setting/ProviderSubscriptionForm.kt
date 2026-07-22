package com.m3u.business.setting

import com.m3u.data.repository.provider.ProviderAccountSummary
import com.m3u.data.repository.provider.ProviderSubscriptionRequest
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.security.CredentialHandle
import com.m3u.extension.api.subscription.ProviderKind
import com.m3u.extension.api.subscription.SubscriptionProviderDescriptor
import com.m3u.extension.api.subscription.SubscriptionProviderSettingKeys
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

enum class ProviderSettingFieldError {
    REQUIRED,
    TOO_LONG,
    INVALID_NUMBER,
    INVALID_BOOLEAN,
    INVALID_CHOICE,
}

data class ProviderSubscriptionFormField(
    val definition: ExtensionSettingField,
    val input: String? = null,
    val error: ProviderSettingFieldError? = null,
) {
    val value: String?
        get() = input
            ?.takeUnless(String::isBlank)
            ?: definition.defaultValue.asSettingValueOrNull(definition.type)

    val isUsingDefault: Boolean
        get() = input.isNullOrBlank() && value != null
}

data class ProviderSubscriptionForm(
    val providerId: ExtensionId,
    val providerKind: ProviderKind,
    val schemaVersion: Int?,
    val fields: List<ProviderSubscriptionFormField>,
    val validationRequested: Boolean = false,
) {
    fun update(fieldKey: String, value: String?): ProviderSubscriptionForm {
        if (fields.none { field -> field.definition.key == fieldKey }) return this
        return copy(
            fields = fields.map { field ->
                if (field.definition.key == fieldKey) {
                    field.copy(input = value, error = null)
                } else {
                    field
                }
            },
        ).validateFields()
    }

    fun buildRequest(
        title: String,
        stageCredential: (String) -> CredentialHandle,
    ): ProviderSubscriptionFormBuildResult {
        val validated = copy(validationRequested = true).validateFields()
        if (validated.fields.any { field -> field.error != null }) {
            return ProviderSubscriptionFormBuildResult.Invalid(validated)
        }

        val settingValues = linkedMapOf<String, String>()
        val secrets = linkedMapOf<String, String>()
        validated.fields.forEach { field ->
            val value = field.value ?: return@forEach
            when (field.definition.type) {
                ExtensionSettingType.SECRET -> secrets[field.definition.key] = value
                else -> settingValues[field.definition.key] = value.normalizedFor(field.definition.type)
            }
        }

        val credentialHandles = secrets.mapValues { (_, secret) -> stageCredential(secret) }
        return ProviderSubscriptionFormBuildResult.Ready(
            ProviderSubscriptionRequest(
                title = title,
                providerId = providerId,
                providerKind = providerKind,
                settingValues = settingValues,
                credentialHandles = credentialHandles,
            )
        )
    }

    private fun validateFields(): ProviderSubscriptionForm = copy(
        fields = fields.map { field ->
            field.copy(
                error = field.definition.validationError(
                    value = field.value,
                    showRequired = validationRequested,
                )
            )
        },
    )

    companion object {
        fun create(
            descriptor: SubscriptionProviderDescriptor,
            providerKind: ProviderKind,
        ): ProviderSubscriptionForm {
            require(descriptor.variants.any { variant -> variant.kind == providerKind }) {
                "Provider ${descriptor.providerId.value} does not support ${providerKind.value}"
            }
            return ProviderSubscriptionForm(
                providerId = descriptor.providerId,
                providerKind = providerKind,
                schemaVersion = descriptor.settingsSchema?.version,
                fields = descriptor.settingsSchema?.fields.orEmpty().map { definition ->
                    ProviderSubscriptionFormField(definition = definition)
                },
            ).validateFields()
        }

        fun createForReauthentication(
            descriptor: SubscriptionProviderDescriptor,
            account: ProviderAccountSummary,
        ): ProviderSubscriptionForm {
            require(descriptor.providerId == account.providerId) {
                "Provider descriptor does not match the account"
            }
            require(descriptor.variants.any { variant -> variant.kind == account.providerKind }) {
                "Provider descriptor does not support the account kind"
            }
            return create(descriptor, account.providerKind)
                .update(SubscriptionProviderSettingKeys.BaseUrl, account.baseUrl)
                .update(SubscriptionProviderSettingKeys.Username, account.username)
        }
    }
}

sealed interface ProviderSubscriptionFormBuildResult {
    data class Ready(
        val request: ProviderSubscriptionRequest,
    ) : ProviderSubscriptionFormBuildResult

    data class Invalid(
        val form: ProviderSubscriptionForm,
    ) : ProviderSubscriptionFormBuildResult
}

private fun ExtensionSettingField.validationError(
    value: String?,
    showRequired: Boolean,
): ProviderSettingFieldError? {
    if (value == null) {
        return ProviderSettingFieldError.REQUIRED.takeIf { required && showRequired }
    }
    if (
        type in setOf(ExtensionSettingType.TEXT, ExtensionSettingType.SECRET) &&
        value.length > MAX_PROVIDER_SETTING_VALUE_LENGTH
    ) {
        return ProviderSettingFieldError.TOO_LONG
    }
    return when (type) {
        ExtensionSettingType.TEXT,
        ExtensionSettingType.SECRET -> null

        ExtensionSettingType.NUMBER -> ProviderSettingFieldError.INVALID_NUMBER.takeUnless {
            value.trim().toDoubleOrNull()?.isFinite() == true
        }

        ExtensionSettingType.BOOLEAN -> ProviderSettingFieldError.INVALID_BOOLEAN.takeUnless {
            value.trim().toBooleanStrictOrNull() != null
        }

        ExtensionSettingType.SINGLE_CHOICE -> ProviderSettingFieldError.INVALID_CHOICE.takeUnless {
            choices.any { choice -> choice.value == value }
        }
    }
}

private const val MAX_PROVIDER_SETTING_VALUE_LENGTH = 4_096

private fun String.normalizedFor(type: ExtensionSettingType): String = when (type) {
    ExtensionSettingType.NUMBER,
    ExtensionSettingType.BOOLEAN -> trim()

    ExtensionSettingType.TEXT,
    ExtensionSettingType.SECRET,
    ExtensionSettingType.SINGLE_CHOICE -> this
}

private fun JsonElement?.asSettingValueOrNull(
    type: ExtensionSettingType,
): String? {
    val primitive = this as? JsonPrimitive ?: return null
    return when (type) {
        ExtensionSettingType.BOOLEAN -> primitive.booleanOrNull?.toString()
        ExtensionSettingType.TEXT,
        ExtensionSettingType.NUMBER,
        ExtensionSettingType.SINGLE_CHOICE -> primitive.contentOrNull

        ExtensionSettingType.SECRET -> null
    }
}
