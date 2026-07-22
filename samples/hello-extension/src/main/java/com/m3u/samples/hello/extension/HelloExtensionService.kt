package com.m3u.samples.hello.extension

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
            val (fieldLabel, defaultValue) = when (request.surface) {
                "phone" -> "Phone name" to "My phone"
                "tv" -> "TV name" to "My TV"
                else -> "Device name" to "My device"
            }
            SettingsSchemaResult(
                sections = listOf(
                    ExtensionSettingSection(
                        id = "device",
                        title = "Device",
                        schema = ExtensionSettingSchema(
                            version = 1,
                            fields = listOf(
                                ExtensionSettingField(
                                    key = "name",
                                    label = fieldLabel,
                                    type = ExtensionSettingType.TEXT,
                                    description = "A setting returned for the ${request.surface} surface",
                                    defaultValue = JsonPrimitive(defaultValue),
                                )
                            ),
                        ),
                    )
                )
            )
        }
    }
}
