package com.m3u.samples.hello.extension

import android.content.Context
import android.content.res.Configuration
import com.m3u.extension.api.ExtensionApiRange
import com.m3u.extension.api.ExtensionApiVersions
import com.m3u.extension.api.ExtensionCapabilityIds
import com.m3u.extension.api.ExtensionCapabilityRequest
import com.m3u.extension.api.ExtensionHookDeclaration
import com.m3u.extension.api.ExtensionId
import com.m3u.extension.api.ExtensionManifest
import com.m3u.extension.api.ExtensionSemanticVersion
import com.m3u.extension.api.ExtensionSettingField
import com.m3u.extension.api.ExtensionSettingSchema
import com.m3u.extension.api.ExtensionSettingSection
import com.m3u.extension.api.ExtensionSettingType
import com.m3u.extension.api.HostHookSpecs
import com.m3u.extension.api.SettingsSchemaResult
import com.m3u.extension.sdk.android.TypedExtensionService
import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive

class HelloExtensionService : TypedExtensionService() {
    override val extensionManifest = ExtensionManifest(
        id = ExtensionId("com.m3u.samples.hello"),
        displayName = "Hello Extension",
        extensionVersion = ExtensionSemanticVersion(1, 0, 0),
        apiRange = ExtensionApiRange(
            minimum = ExtensionApiVersions.Current,
            maximum = ExtensionApiVersions.Current,
        ),
        hooks = setOf(
            ExtensionHookDeclaration(
                hook = HostHookSpecs.SettingsSchema.hook,
                schemaVersion = HostHookSpecs.SettingsSchema.schemaVersion,
                requiredCapabilities = setOf(ExtensionCapabilityIds.SettingsContribute),
            )
        ),
        capabilities = setOf(
            ExtensionCapabilityRequest(
                capability = ExtensionCapabilityIds.SettingsContribute,
                reason = "Add settings for the current device type",
            )
        ),
        settingsSchema = ExtensionSettingSchema(
            version = 1,
            fields = listOf(
                ExtensionSettingField(
                    key = "greeting",
                    label = "Greeting",
                    type = ExtensionSettingType.TEXT,
                    defaultValue = JsonPrimitive("Hello from my extension"),
                )
            ),
        ),
        metadata = mapOf("developer" to "M3UAndroid sample"),
    )

    init {
        handle(HostHookSpecs.SettingsSchema) { request, _ ->
            val copy = helloSettingsCopy(this, request.localeTag, request.surface)
            SettingsSchemaResult(
                sections = listOf(
                    ExtensionSettingSection(
                        id = "device",
                        title = copy.sectionTitle,
                        schema = ExtensionSettingSchema(
                            version = 1,
                            fields = listOf(
                                ExtensionSettingField(
                                    key = "name",
                                    label = copy.fieldLabel,
                                    type = ExtensionSettingType.TEXT,
                                    description = copy.description,
                                    defaultValue = JsonPrimitive(copy.defaultValue),
                                )
                            ),
                        ),
                    )
                )
            )
        }
    }
}

private data class HelloSettingsCopy(
    val sectionTitle: String,
    val fieldLabel: String,
    val description: String,
    val defaultValue: String,
)

private fun helloSettingsCopy(
    context: Context,
    localeTag: String?,
    surface: String,
): HelloSettingsCopy {
    val localizedContext = context.forLocale(localeTag)
    return when (surface) {
        "phone" -> HelloSettingsCopy(
            sectionTitle = localizedContext.getString(R.string.hello_settings_section_device),
            fieldLabel = localizedContext.getString(R.string.hello_settings_phone_name),
            description = localizedContext.getString(R.string.hello_settings_phone_description),
            defaultValue = localizedContext.getString(R.string.hello_settings_phone_default),
        )
        "tv" -> HelloSettingsCopy(
            sectionTitle = localizedContext.getString(R.string.hello_settings_section_device),
            fieldLabel = localizedContext.getString(R.string.hello_settings_tv_name),
            description = localizedContext.getString(R.string.hello_settings_tv_description),
            defaultValue = localizedContext.getString(R.string.hello_settings_tv_default),
        )
        else -> HelloSettingsCopy(
            sectionTitle = localizedContext.getString(R.string.hello_settings_section_device),
            fieldLabel = localizedContext.getString(R.string.hello_settings_device_name),
            description = localizedContext.getString(R.string.hello_settings_device_description),
            defaultValue = localizedContext.getString(R.string.hello_settings_device_default),
        )
    }
}

private fun Context.forLocale(localeTag: String?): Context {
    val locale = localeTag
        ?.trim()
        ?.replace('_', '-')
        ?.takeIf(String::isNotEmpty)
        ?.let(Locale::forLanguageTag)
        ?.takeIf { candidate -> candidate.language.isNotEmpty() }
        ?: return this
    val configuration = Configuration(resources.configuration).apply {
        setLocale(locale)
    }
    return createConfigurationContext(configuration)
}
